(ns dev.figwheel
  "A component for running Figwheel servers."
  (:require [cemerick.piggieback :as piggieback]
            [cljs.repl :as repl]
            [cljs.stacktrace :as stacktrace]
            [clojure.java.io :as io]
            [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.build-utils :as fig-build]
            [figwheel-sidecar.components.css-watcher :as fig-css]
            [figwheel-sidecar.components.cljs-autobuild :as fig-auto]
            [figwheel-sidecar.components.figwheel-server :as fig-server]
            [figwheel-sidecar.config :as fig-config]
            [figwheel-sidecar.repl :as fig-repl]
            [figwheel-sidecar.utils :as fig-util]
            [org.httpkit.server :as httpkit]
            [ring.middleware.cors :as cors]
            [suspendable.core :as suspendable]))

(defrecord FigwheelBuild [])

(defrecord FigwheelServer []
  fig-server/ChannelServer
  (-send-message [server channel-id msg-data callback]
    (let [message (fig-server/prep-message server channel-id msg-data callback)]
      (swap! (:file-change-atom server) fig-server/append-msg message)))
  (-connection-data [server]
    (-> server :connection-count deref)))

(defmethod print-method FigwheelBuild [_ ^java.io.Writer writer]
  (.write writer "#<FigwheelBuild>"))

(defmethod print-method FigwheelServer [_ ^java.io.Writer writer]
  (.write writer "#<FigwheelServer>"))

(defn- figwheel-server [state]
  (-> (compojure/routes
       (GET "/figwheel-ws/:desired-build-id" [] (fig-server/reload-handler state))
       (GET "/figwheel-ws" [] (fig-server/reload-handler state))
       (route/not-found "<h1>Page not found</h1>"))
      (cors/wrap-cors
       :access-control-allow-origin #".*"
       :access-control-allow-methods [:head :options :get :put :post :delete :patch])
      (httpkit/run-server
       {:port      (:server-port state)
        :server-ip (:server-ip state "0.0.0.0")
        :worker-name-prefix "figwh-httpkit-"})))

(defn- start-figwheel-server [opts]
  (let [state  (fig-server/create-initial-state opts)
        server (figwheel-server state)]
    (map->FigwheelServer (assoc state :http-server server))))

(defn- find-files [paths]
  (mapcat (comp file-seq io/file) paths))

(defn- watch-paths [paths]
  (let [time (volatile! 0)]
    (fn []
      (locking time
        (let [now  (System/currentTimeMillis)
              then @time]
          (vreset! time now)
          (filter #(> (.lastModified %) then) (find-files paths)))))))

(defn- prep-build [{:keys [compiler-env source-paths] :as build}]
  (-> build
      (cond-> (not (fig-config/prepped? build)) fig-config/prep-build)
      (cond-> (not compiler-env)                fig-build/add-compiler-env)
      (assoc :watcher (watch-paths source-paths))
      (map->FigwheelBuild)))

(defn- clean-build [build]
  (fig-util/clean-cljs-build* build))

(defn- start-build [build server files]
  (fig-auto/figwheel-build
   {:build-config    (dissoc build :watcher)
    :figwheel-server server
    :changed-files   files}))

(defn rebuild-cljs
  "Tell a Figwheel server component to rebuild all ClojureScript source files,
  and to send the new code to the connected clients."
  [{:keys [server prepped]}]
  (doseq [{:keys [source-paths] :as build} prepped]
    (let [files (map str (find-files source-paths))]
      (fig-util/clean-cljs-build* build)
      (start-build build server files))))

(defn build-cljs
  "Tell a Figwheel server component to build any modified ClojureScript source
  files, and to send the new code to the connected clients."
  [{:keys [server prepped]}]
  (doseq [{:keys [watcher] :as build} prepped]
    (when-let [files (seq (map str (watcher)))]
      (start-build build server files))))

(defn refresh-css
  "Tell a Figwheel server component to update the CSS of connected clients."
  [{:keys [server css-watch]}]
  (fig-css/handle-css-notification {:figwheel-server server} (css-watch)) nil)

(defrecord Server [builds]
  component/Lifecycle
  (start [component]
    (if (:server component)
      component
      (-> component
          (assoc :server  (start-figwheel-server component))
          (assoc :prepped (mapv prep-build builds))
          (cond-> (:css-dirs component)
            (assoc :css-watch (watch-paths (:css-dirs component))))
          (doto
            (build-cljs)
            (refresh-css)))))
  (stop [component]
    (if-let [server (:server component)]
      (do (fig-server/stop-server server)
          (dissoc component :server :prepped :css-watch))
      component))
  suspendable/Suspendable
  (suspend [component] component)
  (resume [component old-component]
    (if (and (:server old-component) (= builds (:builds old-component)))
      (doto (into component (select-keys old-component [:server :prepped :css-watch]))
        (build-cljs)
        (refresh-css))
      (do (component/stop old-component)
          (component/start component)))))

(defn server
  "Create a new Figwheel server with the supplied option map. See the Figwheel
  documentation for a full explanation of what options are allowed."
  [options]
  (map->Server options))

(defn- start-piggieback-repl [server build]
  {:pre [(some? build)]}
  (let [compiler (or (:compiler build) (:build-options build))]
    (piggieback/cljs-repl
     (fig-repl/cljs-repl-env build server)
     :special-fns  (:special-fns compiler repl/default-special-fns)
     :output-dir   (:output-dir compiler "out")
     :compiler-env (:compiler-env build)
     :analyze-path (:source-paths build))))

(defn cljs-repl
  "Open a ClojureScript REPL through the Figwheel server."
  ([{:keys [server prepped]}]
   (start-piggieback-repl server (first prepped)))
  ([{:keys [server prepped]} build-id]
   (start-piggieback-repl server (-> (group-by :id prepped) (get build-id)))))

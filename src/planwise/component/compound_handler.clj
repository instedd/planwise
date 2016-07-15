(ns planwise.component.compound-handler
  "Handler component which composes Duct handlers, otherwise identical in
  functionality to Duct handlers"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :as compojure]))

(defn- find-handler-keys [component]
  (sort (map key (filter (comp :handler val) component))))

(defn- find-handlers [component]
  (map #(:handler (get component %))
       (:handlers component (find-handler-keys component))))

(defn- middleware-fn [component middleware]
  (if (vector? middleware)
    (let [[f & keys] middleware
          arguments  (map #(get component %) keys)]
      #(apply f % arguments))
    middleware))

(defn- compose-middleware [{:keys [middleware] :as component}]
  (->> (reverse middleware)
       (map #(middleware-fn component %))
       (apply comp identity)))

(defrecord CompoundHandler [middleware]
  component/Lifecycle
  (start [component]
    (if-not (:handler component)
      (let [handlers (find-handlers component)
            wrap-mw  (compose-middleware component)
            handler  (wrap-mw (apply compojure/routes handlers))]
        (assoc component :handler handler))
      component))
  (stop [component]
    (dissoc component :handler)))

(defn compound-handler-component [options]
  (map->CompoundHandler options))

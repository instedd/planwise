(ns planwise.system
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.component.hikaricp :refer [hikaricp]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as response]
            [compojure.response :as compojure]

            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.core.nonce :as nonce]
            [clojure.set :refer [rename-keys]]

            [planwise.component.compound-handler :refer [compound-handler-component]]
            [planwise.component.auth :refer [auth-service]]
            [planwise.component.facilities :refer [facilities-service]]
            [planwise.component.routing :refer [routing-service]]
            [planwise.component.projects :refer [projects-service]]
            [planwise.component.regions :refer [regions-service]]
            [planwise.component.users :refer [users-store]]
            [planwise.component.resmap :refer [resmap-client]]
            [planwise.component.importer :refer [importer]]

            [planwise.endpoint.home :refer [home-endpoint]]
            [planwise.endpoint.auth :refer [auth-endpoint]]
            [planwise.endpoint.facilities :refer [facilities-endpoint]]
            [planwise.endpoint.projects :refer [projects-endpoint]]
            [planwise.endpoint.regions :refer [regions-endpoint]]
            [planwise.endpoint.routing :refer [routing-endpoint]]
            [planwise.endpoint.monitor :refer [monitor-endpoint]]
            [planwise.endpoint.datasets :refer [datasets-endpoint]]
            [planwise.endpoint.resmap-auth :refer [resmap-auth-endpoint]]))

(timbre/refer-timbre)


;; TODO: move these to auth endpoint/component?

(defn api-unauthorized-handler
  [request metadata]
  (let [authenticated? (authenticated? request)
        error-response {:error "Unauthorized"}
        status (if authenticated? 403 401)]
    (-> (response/response error-response)
        (response/content-type "application/json")
        (response/status status))))

(defn app-unauthorized-handler
  [request metadata]
  (cond
    (authenticated? request)
    (let [error-response (io/resource "planwise/errors/403.html")]
      (-> (compojure/render error-response request)
          (response/content-type "text/html")
          (response/status 403)))
    :else
    (let [current-url (request-url request)]
      (response/redirect (format "/login?next=%s" current-url)))))


(def jwe-options {:alg :a256kw :enc :a128gcm})
(def jwe-secret (nonce/random-bytes 32))

(def base-config
  {:auth {:jwe-secret  jwe-secret
          :jwe-options jwe-options}
   :api {:middleware   [[wrap-authorization :auth-backend]
                        [wrap-authentication :auth-backend]
                        [wrap-json-params]
                        [wrap-json-response]
                        [wrap-defaults :api-defaults]]
         :api-defaults (meta-merge api-defaults {:params {:nested true}})}
   :api-auth-backend {:unauthorized-handler api-unauthorized-handler}
   :app {:middleware   [[wrap-not-found :not-found]
                        [wrap-webjars]
                        [wrap-resource :jar-resources]
                        [wrap-authorization :auth-backend]
                        [wrap-authentication :auth-backend]
                        [wrap-defaults :app-defaults]]
         :not-found    (io/resource "planwise/errors/404.html")
         :jar-resources "public/assets"
         :app-defaults (meta-merge site-defaults
                                   {:static {:resources "planwise/public"}
                                    :session {:store (cookie-store)
                                              :cookie-attrs {:max-age (* 24 3600)}
                                              :cookie-name "planwise-session"}})}
   :app-auth-backend {:unauthorized-handler app-unauthorized-handler}

   :webapp {:middleware [[wrap-route-aliases :aliases]]
            :aliases    {}

            ; Vector order matters, api handler is evaluated first
            :handlers   [:api :app]}})

(defn jwe-backend
  "Construct a Buddy JWE auth backend from the configuration map"
  [config]
  (let [config (-> config
                   (rename-keys {:jwe-options :options
                                 :jwe-secret :secret})
                   (select-keys [:secret
                                 :options
                                 :unauthorized-handler
                                 :token-name
                                 :on-error]))]
    (backends/jwe config)))

(defn session-backend
  "Construct a Buddy session auth backend from the configuration map"
  [config]
  (let [config (select-keys config [:unauthorized-handler])]
    (backends/session config)))

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :api-auth-backend    (jwe-backend (meta-merge (:auth config)
                                                       (:api-auth-backend config)))
         :app-auth-backend    (session-backend (meta-merge (:auth config)
                                                           (:app-auth-backend config)))

         :app                 (handler-component (:app config))
         :api                 (handler-component (:api config))
         :webapp              (compound-handler-component (:webapp config))
         :http                (jetty-server (:http config))
         :db                  (hikaricp (:db config))

         :auth                (auth-service (:auth config))
         :facilities          (facilities-service)
         :projects            (projects-service)
         :regions             (regions-service)
         :routing             (routing-service)
         :users-store         (users-store)
         :resmap              (resmap-client (:resmap config))
         :importer            (importer)

         :auth-endpoint       (endpoint-component auth-endpoint)
         :home-endpoint       (endpoint-component home-endpoint)
         :facilities-endpoint (endpoint-component facilities-endpoint)
         :projects-endpoint   (endpoint-component projects-endpoint)
         :regions-endpoint    (endpoint-component regions-endpoint)
         :routing-endpoint    (endpoint-component routing-endpoint)
         :monitor-endpoint    (endpoint-component monitor-endpoint)
         :datasets-endpoint   (endpoint-component datasets-endpoint)
         :resmap-auth-endpoint (endpoint-component resmap-auth-endpoint))

        (component/system-using
         {:api                 {:auth-backend :api-auth-backend}
          :app                 {:auth-backend :app-auth-backend}
          :http                {:app :webapp}})

        (component/system-using
         {; Server and handlers
          :webapp              [:app :api]
          :api                 [:monitor-endpoint
                                :facilities-endpoint
                                :regions-endpoint
                                :projects-endpoint
                                :routing-endpoint
                                :datasets-endpoint]
          :app                 [:home-endpoint
                                :auth-endpoint
                                :resmap-auth-endpoint]

          ; Components
          :facilities          [:db]
          :projects            [:db]
          :regions             [:db]
          :routing             [:db]
          :users-store         [:db]
          :auth                [:users-store]
          :resmap              [:auth]
          :importer            [:resmap
                                :facilities
                                :projects]

          ; Endpoints
          :auth-endpoint       [:auth]
          :home-endpoint       [:auth
                                :resmap]
          :facilities-endpoint [:facilities]
          :regions-endpoint    [:regions]
          :projects-endpoint   [:projects]
          :routing-endpoint    [:routing]
          :datasets-endpoint   [:facilities
                                :resmap
                                :importer]
          :resmap-auth-endpoint [:auth
                                 :resmap]}))))

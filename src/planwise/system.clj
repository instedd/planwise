(ns planwise.system
  (:require [integrant.core :as ig])

  #_(:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.util.request :refer [request-url]]
            [ring.util.response :as response]
            [compojure.response :as compojure]
            [clojure.string :as str]

            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.core.nonce :as nonce]
            [clojure.set :refer [rename-keys]]

            [planwise.util.ring :refer [wrap-log-request wrap-log-errors]]
            [planwise.auth.guisso :refer [wrap-check-guisso-cookie]]

            ))





#_(def jwe-options {:alg :a256kw :enc :a128gcm})
#_(def jwe-secret (nonce/random-bytes 32))

#_(def session-config
  {:cookies true
   :session {:store (cookie-store)
             :cookie-attrs {:max-age (* 24 3600)}
             :cookie-name "planwise-session"}})

#_(def base-config
  {:auth {:jwe-secret  jwe-secret
          :jwe-options jwe-options}
   ;; Log requests in the API stack since it'll perform all the params mangling
   ;; and it's executed before the App middleware chain
   :api {:middleware   [[wrap-authorization :jwe-auth-backend]
                        [wrap-authentication :session-auth-backend :jwe-auth-backend]
                        [wrap-log-request :log-request-options]
                        [wrap-keyword-params]
                        [wrap-json-params]
                        [wrap-json-response]
                        [wrap-defaults :api-defaults]]
         :log-request-options {:exclude-uris #"^/(js|css|images|assets)/.*"}
         :api-defaults (meta-merge api-defaults
                                   session-config
                                   {:params {:nested true}})}
   :api-auth-backend {:unauthorized-handler api-unauthorized-handler}
   :app {:middleware   [[wrap-not-found :not-found]
                        [wrap-webjars]
                        [wrap-resource :jar-resources]
                        [wrap-authorization :auth-backend]
                        [wrap-check-guisso-cookie]
                        [wrap-authentication :auth-backend]
                        [wrap-defaults :app-defaults]]
         :not-found    (io/resource "planwise/errors/404.html")
         :jar-resources "public/assets"
         :app-defaults (meta-merge site-defaults
                                   session-config
                                   {:session {:flash true}
                                    :static {:resources "planwise/public"}})}
   :app-auth-backend {:unauthorized-handler app-unauthorized-handler}

   :webapp {:middleware [[wrap-gzip]
                         [wrap-route-aliases :aliases]]
            :aliases    {}

            ; Vector order matters, api handler is evaluated first
            :handlers   [:api :app]}
   :version (if-let [version-io (io/resource "planwise/version")]
              (-> version-io
                (slurp)
                (str/trim-newline)
                (str/trim)))})

#_(defn jwe-backend
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

#_(defn session-backend
  "Construct a Buddy session auth backend from the configuration map"
  [config]
  (let [config (select-keys config [:unauthorized-handler])]
    (backends/session config)))

#_(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :globals             {:app-version (:version config)}

         :api-jwe-auth-backend    (jwe-backend (meta-merge (:auth config)
                                                           (:api-auth-backend config)))
         :api-session-auth-backend    (session-backend (meta-merge (:auth config)
                                                                   (:api-auth-backend config)))
         :app-auth-backend    (session-backend (meta-merge (:auth config)
                                                           (:app-auth-backend config)))

         (component/system-using
         {:api                 {:session-auth-backend :api-session-auth-backend
                                :jwe-auth-backend :api-jwe-auth-backend}
          :app                 {:auth-backend :app-auth-backend}
          :http                {:app :webapp}})

         ))))

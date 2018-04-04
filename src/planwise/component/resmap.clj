(ns planwise.component.resmap
  (:require [planwise.boundary.resmap :as boundary]
            [planwise.boundary.auth :as auth]
            [planwise.model.ident :as ident]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-http.client :as http])
  (:import [java.net URL]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(defn resmap-url
  ([service]
   (resmap-url service nil))
  ([service path]
   (let [path (if (str/starts-with? path "/") path (str "/" path))
         base-url (:url service)
         base-url (if (str/ends-with? base-url "/")
                    (.substring base-url 0 (dec (.length base-url)))
                    base-url)]
     (str base-url path))))

(defn auth-scope
  [service]
  (let [url (URL. (:url service))
        port (.getPort url)]
    (str "app=" (.getHost url) (when (> port 0) (str ":" port)))))

(defn- auth-headers
  [{token :token}]
  (if-not (str/blank? token)
    {"Authorization" (str "Bearer " token)}
    {}))

(defn map-collection
  [resmap-collection]
  {:id (:id resmap-collection)
   :name (:name resmap-collection)
   :description (:description resmap-collection)
   :count (:count resmap-collection)})

(defn list-collections
  ([service]
   (list-collections service nil))
  ([service token]
   (let [url (resmap-url service "/api/collections.json")
         response (http/get url {:headers (auth-headers token)
                                 :throw-exceptions false})]
     (if (= 200 (:status response))
       (->> (json/parse-string (:body response) true)
            (map map-collection))
       (do
         (warn "Failure to retrieve Resourcemap collections:"
               (get-in response [:headers "status"]))
         [])))))

(defn get-collection-fields
  [service token coll-id]
  (let [url (resmap-url service (str "/api/collections/" coll-id "/layers.json"))
        response (http/get url {:headers (auth-headers token)
                                :throw-exceptions false})]
    (if (= 200 (:status response))
      (->> (json/parse-string (:body response) true)
           (mapcat (fn [layer] (:fields layer)))
           (map (fn [field] (-> field
                                (select-keys [:id :name :code :kind :config :metadata])
                                (update :metadata vals)))))
      (do
        (warn "Failure retrieving fields for Resourcemap collection" coll-id
              (get-in response [:headers "status"]))
        []))))

(defn list-user-collections
  [service user-ident]
  (let [scope (auth-scope service)
        auth (:auth service)
        token (auth/find-auth-token auth scope user-ident)]
    (info "Retrieving Resourcemap collections for user" (ident/user-email user-ident))
    (list-collections service token)))

(defn list-collection-fields
  [service user-ident coll-id]
  (let [scope (auth-scope service)
        auth (:auth service)
        token (auth/find-auth-token auth scope user-ident)]
    (info "Retrieving Resourcemap fields information for collection"
          coll-id "for user" (ident/user-email user-ident))
    (get-collection-fields service token coll-id)))

(defn authorised?
  [{:keys [auth] :as service} user-ident]
  (let [scope (auth-scope service)
        token (auth/find-auth-token auth scope user-ident)]
    (not (nil? token))))

(defn fetch-collection-data
  [service token coll-id params]
  (let [url (resmap-url service (str "/api/collections/" coll-id ".json"))
        response (http/get url {:headers (auth-headers token)
                                :throw-exceptions false
                                :query-params params})]
    (if (= 200 (:status response))
      (json/parse-string (:body response) true)
      (do
        (warn "Failure retrieving Resourcemap collection data from" coll-id
              (get-in response [:headers "status"]))
        nil))))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord ResmapClient [url auth]

  boundary/Resmap
  (get-collection-sites [service user-ident coll-id params]
    (let [scope (auth-scope service)
          auth (:auth service)
          token (auth/find-auth-token auth scope user-ident)]
      (fetch-collection-data service token coll-id params)))
  (find-collection-field [service user-ident coll-id field-id]
    (let [fields (list-collection-fields service user-ident coll-id)]
      (some (fn [field]
              (when (or (= (:id field) field-id)
                        (= (:id field) (str field-id)))
                field))
            fields))))


;; ----------------------------------------------------------------------
;; Service initialization

(defmethod ig/init-key :planwise.component/resmap
  [_ config]
  (map->ResmapClient config))

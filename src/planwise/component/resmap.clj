(ns planwise.component.resmap
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [planwise.model.ident :as ident]
            [planwise.component.auth :as auth])
  (:import [java.net URL]))

(timbre/refer-timbre)

(defrecord ResmapClient [url auth])

(defn resmap-client
  [config]
  (map->ResmapClient config))


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
  (let [url (resmap-url service (str "/api/collections/" coll-id "/fields.json"))
        response (http/get url {:headers (auth-headers token)
                                :throw-exceptions false})]
    (if (= 200 (:status response))
      (->> (json/parse-string (:body response) true)
           (mapcat (fn [layer] (:fields layer)))
           (map (fn [field] (select-keys field [:id :name :code :kind :config]))))
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

(defn get-collection-sites
  [service user-ident coll-id params]
  (let [scope (auth-scope service)
        auth (:auth service)
        token (auth/find-auth-token auth scope user-ident)]
    (fetch-collection-data service token coll-id params)))

(defn find-collection-field
  [service user-ident coll-id field-id]
  (let [fields (list-collections service user-ident coll-id)]
    (some (fn [field]
            (when (= (:id field) field-id)
              field))
          fields)))

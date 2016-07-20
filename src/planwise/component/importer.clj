(ns planwise.component.importer
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.core.async :refer [chan put! <! >! go go-loop]]))

(timbre/refer-timbre)

(defn sites-with-location [sites]
  (filter #(and (:lat %) (:long %)) sites))

(defn facility-type-ctor [type-field]
  (let [field-name (:code type-field)
        type-path [:properties (keyword field-name)]]
    (fn [site]
      (let [f_type (get-in site type-path)]
        ;; FIXME: perform mapping to the facilities_types table
        f_type))))

(defn site->facility-ctor [type-field]
  (let [facility-type (facility-type-ctor type-field)]
    (fn [site]
      (-> site
          (select-keys [:id :name :lat :long])
          (rename-keys {:long :lon})
          (assoc :type (facility-type site))))))

(defn sites->facilities [sites type-field]
  (->> sites
       (sites-with-location)
       (map (site->facility-ctor type-field))))

(defn import-collection
  [resmap facilities user coll-id type-field]
  (info "Destroying existing facilities")
  (facilities-component/destroy-facilities! facilities)
  (loop [page 1]
    (let [data (resmap/get-collection-sites resmap user coll-id {:page page})
          sites (:sites data)]
      (when (seq sites)
        (info "Processing page" page "of collection" coll-id)
        (let [new-facilities (sites->facilities sites type-field)]
          (info "Inserting" (count new-facilities) "facilities")
          (facilities-component/insert-facilities! facilities new-facilities))
        (recur (inc page)))))
  (info "Done importing facilities from collection" coll-id))

(defn service-loop
  [{:keys [resmap facilities] :as service}]
  (info "Starting importer service")
  (let [c (:control-channel service)
        status (:status service)]
    (go-loop []
      (let [msg (<! c)]
        (info "Received message" msg)
        (if-not (= msg :quit)
          (do
            (cond
              (= (first msg) :set-status)
              (swap! status (constantly (second msg)))

              (= (first msg) :import!)
              (let [params (second msg)
                    ident (:user params)
                    coll-id (:coll-id params)
                    type-field (:type-field params)]
                (swap! status (constantly :importing))
                (import-collection resmap facilities ident coll-id type-field)
                (swap! status (constantly :importing))
                ))
            (recur))
          (info "Finishing importer service")))))
  service)

(defrecord Importer [status control-channel resmap facilities]
  component/Lifecycle
  (start [component]
    (if-not (:status component)
      (let [c (chan)]
        (-> component
            (assoc :status (atom :ready)
                   :control-channel c)
            (service-loop)))
      component))
  (stop [component]
    (when-let [c (:control-channel component)]
      (put! c :quit))
    (dissoc component :status :control-channel)))

(defn importer
  ([]
   (importer {}))
  ([config]
   (map->Importer config)))

(defn status
  [service]
  (let [status @(:status service)]
    (if (coll? status) (first status) status)))

(defn send-msg
  [service msg]
  (put! (:control-channel service) msg))

(ns planwise.client.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [planwise.client.db :as db]
            [planwise.client.api :as api]
            [cljs.core.async :as async :refer [chan >! <! put!]]
            [re-frame.core :refer [dispatch register-handler]]))


(defn debounced
  ([f timeout]
   (let [id (atom nil)]
     (fn [& args]
       (js/clearTimeout @id)
       (condp = (first args)
         :cancel nil
         :immediate (apply f (drop 1 args))
         (reset! id (js/setTimeout
                     (apply partial (cons f args))
                     timeout)))))))

(defn async-handle [c success-fn]
  (go
    (let [result (<! c)]
      (condp = (:status result)
        :ok (success-fn (:data result))
        :error (.error js/console (str "Error " (:code result) " performing AJAX request: " (:message result)))))))

(defn fetch-geojson []
  (async-handle (api/fetch-geojson)
                #(dispatch [:playground-geojson-received %])))

(defn fetch-isochrone* [node-id threshold & [algorithm]]
  (async-handle (api/fetch-isochrone node-id threshold algorithm)
                #(dispatch [:playground-isochrone-received %])))

(def fetch-isochrone (debounced fetch-isochrone* 500))

(defn fetch-facilities []
  (async-handle (api/fetch-facilities)
                #(dispatch [:playground-facilities-received %])))

(defn isochrone-algorithm [modifier?]
  (if modifier?
    :buffer
    :alpha-shape))

;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (fetch-facilities)
   db/initial-db))

(register-handler
 :navigate
 (fn [db [_ page]]
   (assoc db :current-page page)))

(register-handler
 :update-map-position
 (fn [db [_ map-id new-position]]
   (assoc-in db [map-id :map-view :position] new-position)))

(register-handler
 :update-map-zoom
 (fn [db [_ map-id new-zoom]]
   (assoc-in db [map-id :map-view :zoom] new-zoom)))

(register-handler
 :playground-map-clicked
 (fn [db [_ lat lon modifier?]]
   (async-handle (api/fetch-nearest-node lat lon)
                 #(dispatch [:playground-nearest-node-received % modifier?]))
   (assoc-in db [:playground :loading?] true)))

(register-handler
 :playground-nearest-node-received
 (fn [db [_ {:keys [node-id point]} modifier?]]
   (let [threshold (db/playground-threshold db)
         algorithm (isochrone-algorithm modifier?)]
     (fetch-isochrone :immediate node-id threshold algorithm))
   (-> db
       (assoc-in [:playground :node-id] node-id)
       (assoc-in [:playground :points] [point]))))

(register-handler
 :playground-isochrone-received
 (fn [db [_ isochrone]]
   (-> db
       (assoc-in [:playground :isochrone] isochrone)
       (assoc-in [:playground :loading?] false))))

(register-handler
 :playground-facilities-received
 (fn [db [_ facilities]]
   (assoc-in db [:playground :facilities] facilities)))

(register-handler
 :playground-update-threshold
 (fn [db [_ new-threshold]]
   (let [node-id (db/playground-node-id db)]
     (fetch-isochrone node-id new-threshold :alpha-shape))
   (assoc-in db [:playground :threshold] new-threshold)))

(register-handler
 :playground-reset-view
 (fn [db [_]]
   (assoc-in db [:playground :map-view] db/initial-position-and-zoom)))

(register-handler
 :playground-load-geojson
 (fn [db [_]]
   (fetch-geojson)
   db))

(register-handler
 :playground-geojson-received
 (fn [db [_ geojson]]
   (assoc-in db [:playground :geojson] geojson)))

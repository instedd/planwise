(ns planwise.client.playground.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [planwise.client.db :as db]
            [planwise.client.api :as api]
            [cljs.core.async :as async :refer [chan >! <! put!]]
            [re-frame.core :refer [dispatch register-handler path]]))

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
                #(dispatch [:playground/geojson-received %])))

(defn fetch-isochrone* [node-id threshold & [algorithm]]
  (async-handle (api/fetch-isochrone node-id threshold algorithm)
                #(dispatch [:playground/isochrone-received %])))

(def fetch-isochrone (debounced fetch-isochrone* 500))

(defn fetch-facilities []
  (async-handle (api/fetch-facilities)
                #(dispatch [:playground/facilities-received %])))

(defn isochrone-algorithm [modifier?]
  (if modifier?
    :buffer
    :alpha-shape))


(def in-playground (path [:playground]))

;; Event handlers
;; -----------------------------------------------------------------

(register-handler
 :playground/update-position
 in-playground
 (fn [db [_ new-position]]
   (assoc-in db [:map-view :position] new-position)))

(register-handler
 :playground/update-zoom
 in-playground
 (fn [db [_ new-zoom]]
   (assoc-in db [:map-view :zoom] new-zoom)))

(register-handler
 :playground/map-clicked
 in-playground
 (fn [db [_ lat lon modifier?]]
   (async-handle (api/fetch-nearest-node lat lon)
                 #(dispatch [:playground/nearest-node-received % modifier?]))
   (assoc-in db [:loading?] true)))

(register-handler
 :playground/nearest-node-received
 in-playground
 (fn [db [_ {:keys [node-id point]} modifier?]]
   (let [threshold (:threshold db)
         algorithm (isochrone-algorithm modifier?)]
     (fetch-isochrone :immediate node-id threshold algorithm))
   (assoc db
          :node-id node-id
          :points [point])))

(register-handler
 :playground/isochrone-received
 in-playground
 (fn [db [_ isochrone]]
   (assoc db
          :isochrone isochrone
          :loading? false)))

(register-handler
 :playground/facilities-received
 in-playground
 (fn [db [_ facilities]]
   (assoc db :facilities facilities)))

(register-handler
 :playground/update-threshold
 in-playground
 (fn [db [_ new-threshold]]
   (let [node-id (:node-id db)]
     (fetch-isochrone node-id new-threshold :alpha-shape))
   (assoc db :threshold new-threshold)))

(register-handler
 :playground/reset-view
 in-playground
 (fn [db [_]]
   (assoc db :map-view db/initial-position-and-zoom)))

(register-handler
 :playground/load-geojson
 in-playground
 (fn [db [_]]
   (fetch-geojson)
   db))

(register-handler
 :playground/geojson-received
 in-playground
 (fn [db [_ geojson]]
   (assoc db :geojson geojson)))

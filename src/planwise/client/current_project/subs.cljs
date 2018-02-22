(ns planwise.client.current-project.subs
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]
            [planwise.client.routes :as routes]
            [planwise.client.current-project.db :as db]
            [planwise.client.mapping :as mapping]
            [planwise.client.utils :as utils]))


;; ----------------------------------------------------------------------------
;; Current project subscriptions

(rf/reg-sub
 :current-project/loaded?
 (fn [db _]
   (= (get-in db [:current-project :project-data :id])
      (js/parseInt (get-in db [:page-params :id])))))

(rf/reg-sub
 :current-project/current-data
 (fn [db _]
   (get-in db [:current-project :project-data])))

(rf/reg-sub
 :current-project/wizard-state
 (fn [db _]
   (get-in db [:current-project :wizard])))

(rf/reg-sub
 :current-project/wizard-mode-on
 (fn [db _]
   (get-in db [:current-project :wizard :set])))

(rf/reg-sub
 :current-project/read-only?
 (fn [_ _]
   (rf/subscribe [:current-project/current-data]))
 (fn [current-data _]
   (:read-only current-data)))

(rf/reg-sub
 :current-project/shares
 (fn [db _]
   (get-in db [:current-project :shares])))

(rf/reg-sub
 :current-project/shares-search-string
 (fn [db _]
   (get-in db [:current-project :sharing :shares-search-string])))

(defn matches-share?
  [search-string share]
  (gstring/caseInsensitiveContains (:user-email share) (or search-string "")))

(rf/reg-sub
 :current-project/filtered-shares
 (fn [_ _]
   [(rf/subscribe [:current-project/shares-search-string])
    (rf/subscribe [:current-project/shares])])
 (fn [[search-string shares] _]
   (filterv (partial matches-share? search-string) (asdf/value shares))))

(defn project-share-url
  [project-id token]
  (str (.-origin js/document.location)
       (routes/project-access {:id project-id
                               :token token})))

(rf/reg-sub
 :current-project/share-link
 (fn [db _]
   (let [token (get-in db [:current-project :sharing :token])
         project-id (get-in db [:current-project :project-data :id])]
     (asdf/update token (partial project-share-url project-id)))))

(rf/reg-sub
 :current-project/filter-definition
 (fn [db [_ filter]]
   (get-in db [:current-project :filter-definitions filter])))

(rf/reg-sub
 :current-project/facilities
 (fn [db [_ data]]
   (case data
     :facilities (get-in db [:current-project :facilities :list])
     :isochrones (get-in db [:current-project :facilities :isochrones])
     :filters    (get-in db [:current-project :project-data :filters :facilities])
     :stats      (-> (get-in db [:current-project :project-data :stats])
                     (select-keys [:facilities-targeted :facilities-total])))))

(rf/reg-sub
 :current-project/facilities-by-type
 (fn [_ _]
   [(rf/subscribe [:current-project/facilities :facilities])
    (rf/subscribe [:current-project/filter-definition :facility-type])])
 (fn [[facilities types] [_ data]]
   (->> facilities
        (group-by :type-id)
        (map (fn [[type-id fs]]
               (let [type (->> types
                               (filter #(= type-id (:value %)))
                               (first))]
                 [type fs])))
        (sort-by (fn [[type fs]]
                   (count fs)))
        reverse)))

(rf/reg-sub
 :current-project/facilities-criteria
 (fn [db _]
   (db/facilities-criteria (get-in db [:current-project]))))

(rf/reg-sub
 :current-project/transport-time
 (fn [db _]
   (get-in db [:current-project :project-data :filters :transport :time])))

(rf/reg-sub
 :current-project/map-key
 (fn [db _]
   (get-in db [:current-project :map-key])))

(rf/reg-sub
 :current-project/unsatisfied-demand
 (fn [db _]
   (get-in db [:current-project :unsatisfied-demand])))

(rf/reg-sub
 :current-project/satisfied-demand
 (fn [_ _]
   [(rf/subscribe [:current-project/current-data])
    (rf/subscribe [:current-project/unsatisfied-demand])])
 (fn [[project-data unsatisfied-demand] _]
   (when unsatisfied-demand
     (- (:region-population project-data) unsatisfied-demand))))

(rf/reg-sub
 :current-project/map-state
 (fn [db _]
   (get-in db [:current-project :map-state :current])))

(rf/reg-sub
 :current-project/map-view
 (fn [db [_ field]]
   (let [map-view (get-in db [:current-project :map-view])
         current-region-id (get-in db [:current-project :project-data :region-id])
         current-region-raster-max-value (get-in db [:current-project :project-data :region-max-population])
         current-region-raster-pixel-area (get-in db [:current-project :project-data :region-raster-pixel-area])
         current-region (get-in db [:regions current-region-id])]
     (case field
       :position (:position map-view)
       :zoom (:zoom map-view)
       :bbox (:bbox current-region)
       :pixel-max-value current-region-raster-max-value
       :pixel-area-m2 current-region-raster-pixel-area))))

(rf/reg-sub
 :current-project/map-geojson
 (fn [db _]
   (let [current-region-id (get-in db [:current-project :project-data :region-id])]
     (get-in db [:regions current-region-id :geojson]))))

(rf/reg-sub
 :current-project/view-state
 (fn [db _]
   (get-in db [:current-project :view-state])))

(rf/reg-sub
 :current-project/sharing-emails-text
 (fn [db _]
   (get-in db [:current-project :sharing :emails-text])))

(rf/reg-sub
 :current-project/sharing-emails-state
 (fn [db _]
   (get-in db [:current-project :sharing :state])))

(rf/reg-sub
 :current-project/dataset
 (fn [db _]
   (get-in db [:current-project :dataset])))

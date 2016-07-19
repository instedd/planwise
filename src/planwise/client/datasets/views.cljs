(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]))

(defn open-auth-popup []
  (.open js/window
         "/oauth2/start"
         "PlanwiseAuth"
         "chrome=yes,centerscreen=yes,width=600,height=400"))

(defn collection-item
  [{:keys [id name count]}]
  [:span (str name " (" count " facilities)")])

(defn collections-list
  [collections]
  (let [selected (subscribe [:datasets/selected])]
    (fn [collections]
      (let [selected-coll (:collection @selected)]
        [:ul
         (for [coll collections]
           (let [coll-id (:id coll)
                 selected? (= coll selected-coll)]
             [:li {:key coll-id
                   :class (when selected? "selected")
                   :on-click #(dispatch [:datasets/select-collection coll])}
              [collection-item coll]]))]))))

(defn selected-collection-options
  [{:keys [collection valid? fields type-field] :as selected}]
  [:div
   [:h4 (:name collection)]
   (if (nil? fields)
     [:p "Loading field information..."]
     (if valid?
       [:div
        [:p
         "Collection can be imported as facilities. "
         "Please choose the field to use as the facility type below."]
        [:div
         [:label "Field to import as facility type"]
         [rc/single-dropdown
          :choices fields
          :label-fn :name
          :on-change #(dispatch [:datasets/select-type-field %])
          :model type-field]]
        [:p
         "Important: facilities will be updated from the selected collection. "
         "Facilities not present in the Resourcemap collection will be eliminated."]
        [:button
         {:on-click #(dispatch [:datasets/start-import!])
          :disabled (not (and valid? (some? type-field)))}
         "Import collection"]]
       [:p
        "Collection cannot be imported into facilities."]))])

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])
        selected (subscribe [:datasets/selected])
        facility-count (subscribe [:datasets/facility-count])]
    (fn []
      [:article.datasets
       [:h2 "Facilities"]
       [:p "There are currently " @facility-count " facilities in the system."]
       (if (:authorised? @resourcemap)
         [:div
          [:h3 "Available Resourcemap collections"]
          [:button
           {:on-click #(dispatch [:datasets/reload-info])}
           "Refresh collections"]
          [:p "Select a collection to import facilities from:"]
          [collections-list (:collections @resourcemap)]
          (when (:collection @selected)
            [selected-collection-options @selected])]
         [:div
          [:h3 "Not authorised to access Resourcemap collections"]
          [:button
           {:on-click #(open-auth-popup)}
           "Authorise"]])])))

(defn datasets-page []
  (let [initialised? (subscribe [:datasets/initialised?])]
    (fn []
      [:article
       (if @initialised?
         [datasets-view]
         [:p "Loading..."])])))

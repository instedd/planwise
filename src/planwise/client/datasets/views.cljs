(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]))

(defn initialised?
  [state]
  (and (not= :initialising state) (not (nil? state))))

(defn importing?
  [state]
  (= :importing state))

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
  (let [selected (subscribe [:datasets/selected])
        state (subscribe [:datasets/state])]
    (fn [collections]
      (let [selected-coll (:collection @selected)
            importing? (importing? @state)]
        [:ul
         (for [coll collections]
           (let [coll-id (:id coll)
                 selected? (= coll selected-coll)]
             [:li {:key coll-id
                   :class (when selected? "selected")
                   :on-click (when-not importing?
                               #(dispatch [:datasets/select-collection coll]))}
              [collection-item coll]]))]))))

(defn selected-collection-options
  [{:keys [collection valid? fields type-field] :as selected} importing?]
  (let [can-import? (and (not importing?) valid? (some? type-field))]
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
            :disabled? importing?
            :label-fn :name
            :on-change #(dispatch [:datasets/select-type-field %])
            :model type-field]]
          [:p
           "Important: facilities will be updated from the selected collection. "
           "Facilities not present in the Resourcemap collection will be eliminated."]
          [:button
           {:on-click #(dispatch [:datasets/start-import!])
            :disabled (not can-import?)}
           (if importing? "Importing..." "Import collection")]]
         [:p
          "Collection cannot be imported into facilities."]))]))

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])
        selected (subscribe [:datasets/selected])
        facility-count (subscribe [:datasets/facility-count])
        state (subscribe [:datasets/state])
        raw-status (subscribe [:datasets/raw-status])]
    (fn []
      (let [importing? (importing? @state)]
        [:article.datasets
         [:h2 "Facilities"]
         [:p "There are currently " @facility-count " facilities in the system."]
         (when importing?
           (let [[_ step progress] @raw-status
                 step (or step "Importing collection")]
             [:h3 "Import status: " (str step " " progress)]))
         (if (:authorised? @resourcemap)
           [:div
            [:h3 "Available Resourcemap collections"]
            (when-not importing?
              [:button
               {:on-click #(dispatch [:datasets/reload-info])}
              "Refresh collections"])
            [:p "Select a collection to import facilities from:"]
            [collections-list (:collections @resourcemap)]
            (when (:collection @selected)
              [selected-collection-options @selected importing?])]
           [:div
            [:h3 "Not authorised to access Resourcemap collections"]
            [:button
             {:on-click #(open-auth-popup)}
             "Authorise"]])]))))

(defn datasets-page []
  (let [state (subscribe [:datasets/state])]
    (fn []
      (let [initialised? (initialised? @state)]
        [:article
         (if initialised?
           [datasets-view]
           [:p "Loading..."])]))))

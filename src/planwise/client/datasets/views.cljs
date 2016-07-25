(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :refer [capitalize]]
            [re-com.core :as rc]
            [planwise.client.common :as common]))

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

(defn import-warning []
  [:p.warning
   [:b "Current facilities will be replaced"]
   " by the imported sites. This is a time consuming process and "
   [:b "can take several hours to complete"]
   ", depending on the number of imported facilities."])

(defn selected-collection-options
  []
  (let [selected (subscribe [:datasets/selected])
        state (subscribe [:datasets/state])]
    (fn []
      (let [valid? (:valid? @selected)
            fields (:fields @selected)
            type-field (:type-field @selected)
            importing? (importing? @state)
            can-import? (and (not importing?) valid? (some? type-field))]
        [:div.import-settings
         (if (nil? fields)
           [common/loading-placeholder]
           (if valid?
             [:div
              [:div
               [:label "Use field as facility type "]
               [rc/single-dropdown
                :choices fields
                :disabled? importing?
                :label-fn :name
                :on-change #(dispatch [:datasets/select-type-field %])
                :model type-field]]
              [import-warning]
              [:div.actions
               [:button.primary
                {:on-click #(dispatch [:datasets/start-import!])
                 :disabled (not can-import?)}
                (if importing? "Importing..." "Import collection")]]]
             [:p
              "Collection cannot be imported into facilities."]))]))))

(defn collection-item
  [{:keys [id name description selected? count on-click]}]
  [:li {:class (when selected? "selected")
        :on-click on-click}
   [:h1 name]
   [:p.description description]
   [:p.count (str count " sites")]
   (when selected?
     [selected-collection-options])])

(defn collections-list
  [collections]
  (let [selected (subscribe [:datasets/selected])
        state (subscribe [:datasets/state])]
    (fn [collections]
      (let [selected-coll (:collection @selected)
            importing? (importing? @state)]
        [:ul.collections
         (for [coll collections]
           (let [coll-id (:id coll)
                 selected? (and (not importing?) (= coll selected-coll))]
             [collection-item (assoc coll
                                     :selected? selected?
                                     :key coll-id
                                     :on-click (when-not importing?
                                                 #(dispatch [:datasets/select-collection coll])))]))]))))

(defn facilities-summary []
  (let [facility-count (subscribe [:datasets/facility-count])
        state (subscribe [:datasets/state])
        raw-status (subscribe [:datasets/raw-status])]
    (fn []
      (let [importing? (importing? @state)]
        [:div.dataset-header
         [:h2 "Facilities"]
         (if-not importing?
           [:p "There are " [:b (common/pluralize @facility-count "facility" "facilities")] " in the system."]
           (let [[_ step progress] @raw-status
                 step (or step "Importing collection")]
             [:h3 "Import in progress: " [:b (str (capitalize (name step)) " " progress)]]))]))))

(defn resmap-collections []
  (let [resourcemap (subscribe [:datasets/resourcemap])
        state (subscribe [:datasets/state])]
    (fn []
      (let [importing? (importing? @state)]
        [:div {:class (when importing? "disabled")}
         [:h3 "Available Resourcemap collections "
          (when-not importing?
            [common/refresh-button {:on-click #(dispatch [:datasets/reload-info])}])]
         [collections-list (:collections @resourcemap)]]))))

(defn resmap-authorise []
  [:div
   [:h3 "PlanWise needs authorisation to access your Resourcemap collections."]
   [:button.primary
    {:on-click #(open-auth-popup)}
    "Authorise"]])

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])]
    (fn []
      [:div
       [facilities-summary]
       [:div.resmap
        [:p "You can import facilities from a "
         [:a {:href "http://resourcemap.instedd.org"} "Resourcemap"]
         " collection. The collection to import needs to have the required fields "
         "(facility type, etc.) to be usable. Also only sites with a location will be imported."]
        (if (:authorised? @resourcemap)
          [resmap-collections]
          [resmap-authorise])]])))

(defn datasets-page []
  (let [state (subscribe [:datasets/state])]
    (fn []
      (let [initialised? (initialised? @state)]
        [:article.datasets
         (if initialised?
           [datasets-view]
           [common/loading-placeholder])]))))

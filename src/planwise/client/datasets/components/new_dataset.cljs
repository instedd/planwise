(ns planwise.client.datasets.components.new-dataset
  (:require [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.datasets.db :as db]))

(defn- open-auth-popup
  []
  (.open js/window
         "/oauth2/start"
         "PlanwiseAuth"
         "chrome=yes,centerscreen=yes,width=600,height=400"))

(defn- unauthorised-dialog
  []
  (let [cancel-fn #(dispatch [:datasets/cancel-new-dataset])]
    [:div
     [:p [:b "PlanWise needs authorisation to access your Resourcemap collections."]]
     [:p "You can import facilities from a "
      [:a {:href config/resourcemap-url :target "resmap"} "Resourcemap"]
      " collection. The collection to import needs to have the required fields "
      "(facility type, etc.) to be usable. Also only sites with a location will be imported."]
     [:div.actions
      [:button.primary
       {:type "button"
        :on-click #(open-auth-popup)}
       "Authorise"]
      [:button.cancel
       {:type "button"
        :on-click cancel-fn}
       "Cancel"]]]))

(defn collection-card
  [{:keys [id name description count on-click]}]
  [:li {:on-click on-click}
   [:h1 name]
   [:h2 description]
   [:p.count (str count " sites")]])

(defn collections-list
  [collections]
  [:ul.collections
   (for [coll collections]
     (let [coll-id (:id coll)]
       [collection-card (assoc coll
                               :key coll-id
                               :on-click #(dispatch [:datasets/select-collection coll]))]))])

(defn- authorised-dialog
  []
  (let [view-state (subscribe [:datasets/view-state])
        valid? false
        cancel-fn #(dispatch [:datasets/cancel-new-dataset])
        resmap (subscribe [:datasets/resourcemap])]
    (fn []
      (let [collections (:collections @resmap)]
        [:div
         [collections-list collections]
         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (or (= @view-state :creating)
                          (not valid?))}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:type "button"
            :on-click cancel-fn}
           "Cancel"]]]))))

(defn new-dataset-dialog
  []
  (let [resmap (subscribe [:datasets/resourcemap])]
    (fn []
      (let [loaded? (db/resmap-loaded? @resmap)
            authorised? (db/resmap-authorised? @resmap)
            cancel-fn #(dispatch [:datasets/cancel-new-dataset])
            key-handler-fn #(case (.-which %)
                              27 (cancel-fn)
                              nil)]
        [:form.dialog.new-dataset
         {:on-key-down key-handler-fn
          :on-submit (utils/prevent-default
                      #(dispatch [:datasets/create-dataset]))}
         [:div.title
          [:h1 "New Dataset"]
          [common/close-button {:on-click cancel-fn}]]

         (if loaded?
           (if authorised?
             [authorised-dialog]
             [unauthorised-dialog])
           (do
             (dispatch [:datasets/load-resourcemap-info])
             [common/loading-placeholder "Loading Resourcemap collections..."]))]))))

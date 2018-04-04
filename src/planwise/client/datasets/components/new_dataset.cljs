(ns planwise.client.datasets.components.new-dataset
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
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
      :on-click #(dispatch [:datasets/cancel-new-dataset])}
     "Cancel"]]])

(defn no-collections-found
  []
  [:div
   [:p [:b "No usable collections found."]]
   [:p
    "Collections can be imported from "
    [:a {:href config/resourcemap-url :target "resmap"} "Resourcemap"]
    " if they have the required fields (facility type, etc.). "
    "You can only create a single dataset per collection."]
   [:p
    [:a
     {:href (str config/resourcemap-url "/collections/new") :target "resmap"}
     "Create a new collection"]
    " and try again."]
   [:div.actions
    [:button.primary
     {:type "button"
      :on-click #(dispatch [:datasets/load-resourcemap-info])}
     "Reload"]
    [:button.cancel
     {:type "button"
      :on-click #(dispatch [:datasets/cancel-new-dataset])}
     "Cancel"]]])

(defn collection-card
  [{:keys [name description count on-click]}]
  [:div.collection-card {:on-click on-click}
   [:h1 name]
   [:h2 description]
   [:p.count (utils/pluralize count "site")]])

(defn collections-list
  [collections on-select-collection-fn]
  [:ul.collections
   (for [coll collections]
     (let [coll-id (:id coll)]
       [:li
        {:key coll-id}
        [collection-card (assoc coll :on-click #(on-select-collection-fn coll))]]))])

(defn- authorised-dialog
  []
  (let [state (subscribe [:datasets/state])
        new-dataset-data (subscribe [:datasets/new-dataset-data])
        cancel-fn #(dispatch [:datasets/cancel-new-dataset])
        resmap (subscribe [:datasets/resourcemap])]
    (fn []
      (let [collections (:collections (asdf/value @resmap))
            selected (:collection @new-dataset-data)
            type-field (:type-field @new-dataset-data)
            fields (:fields selected)
            valid? (db/new-dataset-valid? @new-dataset-data)]
        (if (seq collections)
          [:div
           [:div.content
            (if (nil? selected)
              [collections-list collections #(dispatch [:datasets/update-new-dataset :collection %])]
              [:div
               [collection-card selected]
               [:div.form-control-2
                [:label "Use field as facility type "]
                [rc/single-dropdown
                 :choices fields
                 :label-fn :name
                 :on-change #(dispatch [:datasets/update-new-dataset :type-field %])
                 :model type-field]]
               [:p.warning
                "This is a time consuming process and "
                [:b "can take several hours to complete"]
                ", depending on the number of imported facilities."]])]
           [:div.actions
            [:button.primary
             {:type "submit"
              :disabled (or (= @state :creating)
                            (not valid?))}
             (if (= @state :creating)
               "Importing..."
               "Import")]
            [:button.cancel
             {:type "button"
              :on-click cancel-fn}
             "Cancel"]]]
          [no-collections-found])))))

(defn new-dataset-dialog
  []
  (let [resmap (subscribe [:datasets/resourcemap])]
    (fn []
      (let [loaded? (asdf/valid? @resmap)
            authorised? (db/resmap-authorised? (asdf/value @resmap))
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
             (when (not (asdf/reloading? @resmap))
               (dispatch [:datasets/load-resourcemap-info]))
             [common/loading-placeholder "Loading Resourcemap collections..."]))]))))

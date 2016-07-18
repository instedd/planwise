(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]))

(defn open-auth-popup []
  (.open js/window
         "/oauth2/start"
         "PlanwiseAuth"
         "chrome=yes,centerscreen=yes,width=600,height=400"))

(defn collection-item
  [{:keys [id name count]}]
  [:li (str name " (" count " facilities)")])

(defn collections-list
  [collections]
  [:ul
   (for [coll collections] [collection-item coll])])

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])
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
          [collections-list (:collections @resourcemap)]]
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
         [:p "Initialising..."])])))

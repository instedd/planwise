(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]))

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])
        facility-count (subscribe [:datasets/facility-count])]
    (fn []
      [:article.datasets
       [:h2 "Facilities"]
       [:p "There are " @facility-count " facilities in the system."]
       (if (:authorised? @resourcemap)
         [:div
          [:h3 "Available Resourcemap collections"]]
         [:div
          [:h3 "Not authorised to access Resourcemap collections"]
          [:button "Authorise"]])])))

(defn datasets-page []
  (let [initialised? (subscribe [:datasets/initialised?])]
    (fn []
      [:article
       (if @initialised?
         [datasets-view]
         [:p "Initialising..."])])))

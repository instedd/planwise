(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.mapping :refer [default-base-tile-layer]]
            [leaflet.core :refer [map-widget]]))

(defn search-box []
  [:div.search-box
   [:div "0 Projects"]
   [:input {:type "search"}]])

(defn no-projects-view []
  [:div.empty-list
   [:img {:src "/images/empty-projects.png"}]
   [:p "You have no projects yet"]
   [:div
    [:button.primary
     {:on-click
      #(dispatch [:projects/begin-new-project])}
     "New Project"]]])

(defn new-project-dialog []
  [:div.dialog
   [:div.title
    [:h1 "New Project"]
    [:button.close {:on-click
                    #(dispatch [:projects/cancel-new-project])}
     "X"]]
   [:div.form-control
    [:label "Goal"]
    [:input {:type "text" :placeholder "Describe your project's goal"}]]
   [:div.form-control
    [:label "Location"]
    [:input {:type "search" :placeholder "Enter your project's location"}]]
   [map-widget {:width 400
                :height 300
                :position [0 0]
                :zoom 1
                :controls []}
    default-base-tile-layer]
   [:div.actions
    [:button.primary "Continue"]
    [:button.cancel
     {:on-click
      #(dispatch [:projects/cancel-new-project])}
     "Cancel"]]])

(defn modal-dialog [{:keys [on-backdrop-click] :as props} & children]
  (let [children (if (map? props) children [props])]
    [:div.modal
     [:div.backdrop]
     (into [:div.modal-container {:on-click
                                  (fn [e] (when (and (= (aget e "target")
                                                        (aget e "currentTarget"))
                                                     on-backdrop-click)
                                            (on-backdrop-click)))}]
           children)]))

(defn list-view []
  (let [creating-project? (subscribe [:projects/creating?])]
    (fn []
      [:div
       [search-box]
       [no-projects-view]
       (when @creating-project?
         [modal-dialog {:on-backdrop-click
                        #(dispatch [:projects/cancel-new-project])}
          [new-project-dialog]])])))

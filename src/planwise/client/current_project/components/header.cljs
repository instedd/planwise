(ns planwise.client.current-project.components.header
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.components.nav :as nav]
            [planwise.client.routes :as routes]
            [planwise.client.asdf :as asdf]
            [re-frame.utils :as c]
            [planwise.client.datasets.db :refer [dataset->warning-text]]))

(defn- dataset-status []
  (let [dataset-sub (subscribe [:current-project/dataset])]
    (fn []
      (when (asdf/should-reload? @dataset-sub)
        (dispatch [:current-project/load-dataset]))
      (when-let [warning (dataset->warning-text (asdf/value @dataset-sub))]
        [:span {:title warning} warning]))))


(defn project-tab-items [project-id]
  (let [route-params {:id project-id}]
    [{:item :demographics
      :href (routes/project-demographics route-params)
      :title "Demographics"
      :icon :demographics}
     {:item :facilities
      :href (routes/project-facilities route-params)
      :title "Facilities"
      :icon :location}
     {:item :transport
      :href (routes/project-transport route-params)
      :title "Transport Means"
      :icon :transport-means}
     #_{:item :scenarios
        :href (routes/project-scenarios route-params)
        :title "Scenarios"}]))

(defn header-section [project-id project-goal selected-tab read-only share-count]
  [:div.project-header
   [:div.title
    [:h2 project-goal]
    [dataset-status]]
   [:nav
    [nav/ul-menu (project-tab-items project-id) selected-tab]
    (if read-only
      [:div
       [:button.delete
        {:on-click (utils/with-confirm
                    #(dispatch [:current-project/leave-project])
                    "Are you sure you want to leave this shared project?")}
        (common/icon :exit "icon-small")
        "Leave project"]]
      [:div
       [:button.secondary
        {:on-click (utils/prevent-default
                     #(dispatch [:current-project/open-share-dialog]))}
        (common/icon :share "icon-small")
        (if (pos? share-count)
          (str "Shared with " (utils/pluralize share-count "user"))
          "Share")]
       [:button.delete
        {:on-click (utils/with-confirm
                     #(dispatch [:current-project/delete-project])
                     "Are you sure you want to delete this project?")}
        (common/icon :delete "icon-small")
        "Delete project"]])]])

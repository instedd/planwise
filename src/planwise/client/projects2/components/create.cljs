(ns planwise.client.projects2.components.create
  (:require [re-frame.core :refer [subscribe dispatch] :as rf]
            [planwise.client.asdf :as asdf]
            [reagent.core :as r]
            [re-com.core :as rc]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [planwise.client.utils :as utils]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]
            [planwise.client.components.common :as common]))
(def project-templates
  [{:description "Plan facilities based on ground access"
    :icon "directions_walk"
    :key "plan"
    :defaults {:name "ground"}}
   {:description "Plan diagonostic devices & sample referrals"
    :icon "call_split"
    :key "diagnosis"
    :defaults {:name "sample"}}])

(defn project-section-template-selector
  []
  [ui/fixed-width (common2/nav-params)
   (let [templates (subscribe [:projects2/templates])
         scratch-template (first (filter #(not (contains? % :description)) @templates))
         sample-templates (filter #(contains? % :description) @templates)]
     (dispatch [:projects2/get-templates-list])
     [:div.template-container
      (if (some? sample-templates)
        [:h2 "Start from a template"])
      (if (some? sample-templates)
        [:div.row
         (map (fn [template]
                [:a.action {:key (:key template) :onClick #(dispatch [:projects2/new-project (:defaults template)])}
                 [m/Icon {} (:icon template)]
                 [:div (:description template)]])
              sample-templates)])
      [:hr]
      [:h2 "Start from scratch"]
      [:div.row
       [:a.action {:onClick #(dispatch [:projects2/new-project (:defaults scratch-template)])}
        [m/Icon {} "folder_open"]
        [:div "Follow a wizard through all available settings"]]]])])

(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [planwise.client.scenarios.db :as db]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [planwise.client.mapping :as mapping]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]))

(defn simple-map []
  (let [position  (r/atom mapping/map-preview-position)
        zoom      (r/atom 3)]
    (fn []
      [:div.map-container [l/map-widget {:zoom @zoom
                                         :position @position
                                         :on-position-changed #(reset! position %)
                                         :on-zoom-changed #(reset! zoom %)
                                         :controls []}
                           mapping/default-base-tile-layer]])))

(defn- create-new-scenario
  [current-scenario]
  [m/Button {:id "create-new-scenario"
             :on-click #(dispatch [:scenarios/copy-scenario (:id current-scenario)])}
   "Create new scenario from here"])

(defn display-current-scenario
  [current-scenario]
  (let [{:keys [name investment demand-coverage]} current-scenario]
    [ui/full-screen (merge {:main-prop {:style {:position :relative}}
                            :main [simple-map]}
                           (common2/nav-params))
     [:h1 "Scenario " name]
     [:hr]
     [:p "INCREASE IN PREAGNANCIES COVERAGE"]
     [:h2 "0 " "(0%)"]
     [:h "to a total of " demand-coverage]
     [:p "INVESTMENT REQUIRED"]
     [:h2 "K " investment]
     [:hr]
     [create-new-scenario current-scenario]]))

(defn scenarios-page []
  (let [page-params (subscribe [:page-params])
        current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (fn []
      (let [{:keys [id project-id]} @page-params]
        (cond
          (not= id (:id @current-scenario)) (dispatch [:scenarios/get-scenario id])
          (not= project-id (:id @current-project)) (dispatch [:projects2/get-project project-id])
          (not= project-id (:project-id @current-scenario)) (dispatch [:scenarios/scenario-not-found])
          :else
          [display-current-scenario  @current-scenario])))))

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
     [:hr]]))


(defn scenarios-page []
  (let [page-params (subscribe [:page-params])
        {:keys [id]} @page-params
        current-scenario (subscribe [:scenarios/current-scenario])]
    (fn []
      (cond (nil? @current-scenario)
            (dispatch [:scenarios/load-scenario id])
            :else
            [display-current-scenario @current-scenario]))))

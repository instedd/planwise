(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [planwise.client.scenarios.db :as db]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [planwise.client.styles :as styles]
            [planwise.client.mapping :as mapping]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.changeset :as changeset]
            [planwise.client.components.common :as common]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.rmwc :as m]))

(defn- find-index-by-location
  [loc changeset]
  (.indexOf changeset (first (filter #(= (:location %) loc) changeset))))

(defn- get-location
  [object]
  (let [data (:latlng (js->clj object :keywordize-keys true))
        {:keys [lat lng]} data]
    {:lat lat :lon lng}))

(defn simple-map
  [{:keys [changeset]}]
  (let [state    (subscribe [:scenarios/view-state])
        index    (subscribe [:scenarios/changeset-index])
        position (r/atom mapping/map-preview-position)
        zoom     (r/atom 3)
        add-point (fn [lat lon] (dispatch [:scenarios/create-site {:lat lat
                                                                   :lon lon}]))]
    (fn [{:keys [changeset]}]
      (let [indexed-changeset   (map (fn [elem] {:elem elem :index (.indexOf changeset elem)}) changeset)]
        [:div.map-container [l/map-widget {:zoom @zoom
                                           :position @position
                                           :on-position-changed #(reset! position %)
                                           :on-zoom-changed #(reset! zoom %)
                                           :on-click (cond  (= @state :new-site) add-point)
                                           :controls []}
                             mapping/default-base-tile-layer
                             [:marker-layer {:points indexed-changeset
                                             :lat-fn #(get-in % [:elem :location :lat])
                                             :lon-fn #(get-in % [:elem :location :lon])
                                             :radius 4
                                             :fillColor styles/orange
                                             :stroke false
                                             :fillOpacity 1
                                             :event-fn #(let [index  (find-index-by-location (get-location %) changeset)]
                                                          (dispatch [:scenarios/open-changeset-dialog index]))}]]]))))

(defn- create-new-scenario
  [current-scenario]
  [m/Button {:id "create-new-scenario"
             :on-click #(dispatch [:scenarios/copy-scenario (:id current-scenario)])}
   "Create new scenario from here"])

(defn display-current-scenario
  [current-scenario]
  (let [{:keys [name investment demand-coverage]} current-scenario]
    [ui/full-screen (merge {:main-prop {:style {:position :relative}}
                            :main [simple-map current-scenario]}
                           (common2/nav-params))
     [:div [:h1 name] [edit/rename-button]]
     [:hr]
     [:p "INCREASE IN PREAGNANCIES COVERAGE"]
     [:h2 "0 " "(0%)"]
     [:h "to a total of " demand-coverage]
     [:p "INVESTMENT REQUIRED"]
     [:h2 "K " investment]
     (cond (not= (:label current-scenario) "initial") [m/Fab {:on-click #(dispatch [:scenarios/adding-new-site])} "star"])
     [changeset/listing-component current-scenario]
     [:hr]
     [create-new-scenario current-scenario]
     [edit/rename-scenario-dialog]
     [edit/changeset-dialog current-scenario]]))

(defn scenarios-page []
  (let [page-params (subscribe [:page-params])
        state (subscribe [:scenarios/view-state])
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

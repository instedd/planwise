(ns planwise.client.scenarios.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [leaflet.core :as l]
            [planwise.client.config :as config]
            [planwise.client.scenarios.db :as db]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [planwise.client.styles :as styles]
            [planwise.client.mapping :as mapping]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.changeset :as changeset]
            [planwise.client.components.common :as common]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]))

(defn- show-provider
  [{{:keys [action name capacity investment]} :elem :as provider}]
  (if (nil? action)
    (str "<b>" (utils/escape-html name) "</b><br> Capacity: " capacity)
    (str "<b> New provider " (:index provider) "</b><br> Click on panel for editing... ")))

(defn- show-source
  [{{:keys [name quantity quantity-current]} :elem :as source}]
  (str "<b>" (utils/escape-html name) "</b>"
       "<br> Original quantity: " quantity
       "<br> Current quantity: " quantity-current))

(defn- to-indexed-map
  [coll]
  (map-indexed (fn [idx elem] {:elem elem :index idx}) coll))

(defn simple-map
  [{:keys [bbox]} scenario]
  (let [state    (subscribe [:scenarios/view-state])
        index    (subscribe [:scenarios/changeset-index])
        position (r/atom mapping/map-preview-position)
        zoom     (r/atom 3)
        add-point (fn [lat lon] (dispatch [:scenarios/create-provider {:lat lat
                                                                       :lon lon}]))
        use-providers-clustering false
        providers-type-layer     (if use-providers-clustering :cluster-layer :point-layer)]
    (fn [{:keys [bbox]} {:keys [changeset providers raster] :as scenario}]
      (let [providers             (into providers changeset)
            indexed-providers     (to-indexed-map providers)
            indexed-sources       (to-indexed-map (:sources scenario))
            pending-demand-raster raster]
        [:div.map-container [l/map-widget {:zoom @zoom
                                           :position @position
                                           :on-position-changed #(reset! position %)
                                           :on-zoom-changed #(reset! zoom %)
                                           :on-click (cond  (= @state :new-provider) add-point)
                                           :controls []
                                           :initial-bbox bbox}
                             mapping/default-base-tile-layer
                             (when pending-demand-raster
                               [:wms-tile-layer {:url config/mapserver-url
                                                 :transparent true
                                                 :layers "scenario"
                                                 :DATAFILE (str pending-demand-raster ".map")
                                                 :format "image/png"
                                                 :opacity 0.6}])
                             [:point-layer {:points indexed-sources
                                            :lat-fn #(get-in % [:elem :lat])
                                            :lon-fn #(get-in % [:elem :lon])
                                            :options-fn #(select-keys % [:index])
                                            :style-fn #(let [source (:elem %)
                                                             quantity-initial (:quantity source)
                                                             quantity-current (:quantity-current source)
                                                             ratio (/ quantity-current quantity-initial)
                                                             color (cond
                                                                     (<= ratio 0.25) {:fill :limegreen :stroke :limegreen}
                                                                     (< 0.25 ratio 0.5) {:fill :yellow :stroke :yellow}
                                                                     (<= 0.5 ratio 0.75) {:fill :orange :stroke :orange}
                                                                     (> ratio 0.75) {:fill :red :stroke :red})]
                                                         {:fillColor (:fill color)
                                                          :color (:stroke color)})
                                            :radius 7
                                            :fillOpacity 0.8
                                            :stroke true
                                            :weight 10
                                            :opacity 0.2
                                            :popup-fn #(show-source %)}]
                             [providers-type-layer {:points indexed-providers
                                                    :lat-fn #(get-in % [:elem :location :lat])
                                                    :lon-fn #(get-in % [:elem :location :lon])
                                                    :options-fn #(select-keys % [:index])
                                                    :radius 4
                                                    :fillColor "#444"
                                                    :fillOpacity 0.9
                                                    :stroke false
                                                    :popup-fn #(show-provider %)
                                                    :onclick-fn (fn [e] (when (get-in e [:elem :action])
                                                                          (dispatch [:scenarios/open-changeset-dialog (-> e .-layer .-options .-index)])))}]]]))))

(defn- create-new-scenario
  [current-scenario]
  [m/Button {:class-name "btn-create"
             :on-click #(dispatch [:scenarios/copy-scenario (:id current-scenario)])}
   "Create new scenario from here"])

(defn- format-percentage
  [num denom]
  (utils/format-percentage (/ num denom) 2))

(defn initial-scenario-panel
  [{:keys [name demand-coverage state]} unit-name source-demand]
  [:div
   [:div {:class-name "section"}
    [:h1 {:class-name "title-icon"} name]]
   [:hr]
   [:div {:class-name "section"}
    [:h1 {:class-name "large"}
     [:small (str "Initial " unit-name " coverage")]
     (cond
       (= "pending" state) "loading..."
       :else (str (utils/format-number demand-coverage) " (" (format-percentage demand-coverage source-demand) ")"))]
    [:p {:class-name "grey-text"}
     (str "of a total of " (utils/format-number source-demand))]]])

(defn side-panel-view
  [{:keys [name label investment demand-coverage increase-coverage state]} unit-name source-demand]
  [:div
   [:div {:class-name "section"
          :on-click  #(dispatch [:scenarios/open-rename-dialog])}
    [:h1 {:class-name "title-icon"} name]]
   [:hr]
   [:div {:class-name "section"}
    [:h1 {:class-name "large"}
     [:small (str "Increase in " unit-name " coverage")]
     (cond
       (= "pending" state) "loading..."
       :else (str increase-coverage " (" (format-percentage increase-coverage source-demand) ")"))]
    [:p {:class-name "grey-text"}
     (cond
       (= "pending" state) "to a total of"
       :else (str "to a total of " (utils/format-number demand-coverage) " (" (format-percentage demand-coverage source-demand) ")"))]]
   [:div {:class-name "section"}
    [:h1 {:class-name "large"}
     [:small "Investment required"]
     "K " (utils/format-number investment)]]
   [:hr]
   [m/Fab {:class-name "btn-floating"
           :on-click #(dispatch [:scenarios/adding-new-provider])} "domain"]])

(defn display-current-scenario
  [current-project current-scenario]
  (let [read-only? (subscribe [:scenarios/read-only?])
        created-providers (subscribe [:scenarios/created-providers])
        source-demand (get-in current-project [:engine-config :source-demand])
        unit-name  (get-in current-project [:config :demographics :unit-name])
        export-providers-button [:a {:class "mdc-fab disable-a"
                                     :id "main-action"
                                     :href (str "/api/scenarios/" (:id current-scenario) "/csv")}
                                 [m/Icon {:class "material-icons  center-download-icon"} "get_app"]]]
    (fn [current-project current-scenario]
      [ui/full-screen (merge (common2/nav-params)
                             {:main-prop {:style {:position :relative}}
                              :main [simple-map current-project current-scenario]
                              :title [:ul {:class-name "breadcrumb-menu"}
                                      [:li [:a {:href (routes/projects2-show {:id (:id current-project)})} (:name current-project)]]
                                      [:li [m/Icon {:strategy "ligature" :use "keyboard_arrow_right"}]]
                                      [:li (:name current-scenario)]]
                              :action export-providers-button})
       (if @read-only?
         [initial-scenario-panel current-scenario unit-name source-demand]
         [side-panel-view current-scenario unit-name source-demand])
       [:div {:class-name "fade"}]
       [changeset/listing-component @created-providers]
       [:div {:class-name "fade inverted"}]
       [create-new-scenario current-scenario]
       [edit/rename-scenario-dialog]
       [edit/changeset-dialog current-scenario (get-in current-project [:config :actions :budget])]])))

(defn scenarios-page
  []
  (let [page-params (subscribe [:page-params])
        state (subscribe [:scenarios/view-state])
        current-scenario (subscribe [:scenarios/current-scenario])
        current-project  (subscribe [:projects2/current-project])]
    (r/create-class
     {:reagent-render
      (fn []
        (let [{:keys [id project-id]} @page-params]
          (cond
            (not= id (:id @current-scenario)) (dispatch [:scenarios/get-scenario id])
            (not= project-id (:id @current-project)) (dispatch [:projects2/get-project project-id])
            (not= project-id (:project-id @current-scenario)) (dispatch [:scenarios/scenario-not-found])
            :else
            [display-current-scenario @current-project @current-scenario])))

      :component-will-unmount
      (fn []
        (dispatch [:scenarios/clear-current-scenario]))})))

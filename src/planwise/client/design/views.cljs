(ns planwise.client.design.views
  (:require [re-frame.core :refer [subscribe]]
            [reagent.core :as r]
            [leaflet.core :as l]
            [planwise.client.config :as config]
            [planwise.client.mapping :as mapping]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.components.common2 :as common2]
            [planwise.client.ui.common :as ui]))

(def dialog-open (r/atom false))

(defn- project-tabs
  [{:keys [active] :or {active 0}}]
  [m/TabBar {:activeTabIndex active}
   [m/Tab "Lorem"]
   [m/Tab "Ipsum"]])

(def nav-params
  {:sections [[ui/section {:href "/_design" :className "active"} "Design"]
              [ui/section {:href "/_design/map"} "Map"]
              [ui/section {:href "/_design/project"} "Project"]
              [ui/section {:href "/_design/scenario"} "Scenario"]]
   :account [ui/account {:name "John Doe" :on-signout #(println "Sign out")}]
   :title "Planwise"
   :action (ui/main-action {:icon "add" :on-click #(reset! dialog-open true)})
   :footer [ui/footer]})

(defn project-card
  [{:keys [title href]}]
  [ui/card {:href href
            :primary [:img {:src "http://via.placeholder.com/373x278"}]
            :title title}])

(defn demo-list
  []
  [ui/fixed-width nav-params
   [ui/card-list {}
    [project-card {:title "Lorem ipsum" :href "/_design/project?id=1"}]
    [project-card {:title "Dolor sit"   :href "/_design/project?id=2"}]
    [project-card {:title "Amet"        :href "/_design/project?id=3"}]]
   [m/SimpleDialog
    {:title "The title" :body "The Body" :open @dialog-open :onClose #(reset! dialog-open false)}]])

(defn demo-project
  []
  [ui/fixed-width (merge {:tabs [project-tabs {:active 1}]} nav-params)
   [ui/panel {}
    [m/Grid {}
     [m/GridCell {:span 6}
      [:form.vertical
       [:h2 "Goal"]
       [common2/text-field {:label "Lorem"}]
       [m/TextFieldHelperText {} "Enter the goal for this project"]
       [common2/text-field {:label "Ipsum"}]
       [m/Checkbox {} "dolor sit amet"]
       [m/Select {:label "Foo"
                  :options ["bar" "baz" "qux"]}]]]
     [m/GridCell {:span 6}
      [ui/panel {}
       [:pre {} "MAP"]]]
     [m/GridCell {:span 12}
      [:div.form-actions
       [m/Button {} "Continue"]]]]]])

(defn simple-map
  [opts]
  (let [colors    [:red :blue :green :yellow :lime]
        position  (r/atom mapping/map-preview-position)
        zoom      (r/atom 3)
        points    (r/atom [])
        add-point (fn [lat lon] (swap! points conj {:lat lat
                                                    :lon lon
                                                    :weight 3
                                                    :color (first (shuffle colors))
                                                    :radius (+ 5 (rand-int 5))}))]
    (fn [{:keys [layer-name map-datafile] :as opts}]
      [:div.map-container [l/map-widget {:zoom @zoom
                                         :position @position
                                         :on-position-changed #(reset! position %)
                                         :on-zoom-changed #(reset! zoom %)
                                         :on-click add-point
                                         :controls []}
                           mapping/default-base-tile-layer
                           (when opts
                             [:wms-tile-layer {:url config/mapserver-url
                                               :transparent true
                                               :layers layer-name
                                               :DATAFILE map-datafile
                                               :format "image/png"
                                               :opacity 0.6}])
                           [:point-layer {:points @points
                                          :style-fn #(select-keys % [:weight :radius :color])}]]])))

(defn demo-map
  []
  (let [layer-name    (r/atom "population")
        map-datafile  (r/atom "populations/maps/20/1")
        build-options (fn [] {:layer-name   @layer-name
                              :map-datafile @map-datafile})
        map-options   (r/atom (build-options))]
    (fn []
      [ui/full-screen (merge {:tabs              [project-tabs {:active 1}]
                              :secondary-actions [[ui/secondary-action {:on-click #(println "clicked lorem!")} "Lorem"]
                                                  [ui/secondary-action {:on-click #(println "clicked ipsum!")} "Ipsum"]]
                              :main-prop         {:style {:position :relative}}
                              :main              [simple-map @map-options]}
                             nav-params)
       [:div {:style {:padding "0 15px"}}
        [:h1 "Demo Map"]
        [:form.vertical {:on-submit (utils/prevent-default (fn [_] (reset! map-options (build-options))))}
         [common2/text-field {:label     "Layer name"
                              :value     @layer-name
                              :on-change #(reset! layer-name (-> % .-target .-value))}]
         [common2/text-field {:label     "Map DATAFILE"
                              :value     @map-datafile
                              :on-change #(reset! map-datafile (-> % .-target .-value))}]
         [m/Button "Apply"]]]])))

(defn demo-scenario
  []
  [ui/full-screen (merge {:tabs [project-tabs {:active 1}]
                          :secondary-actions [[ui/secondary-action {:on-click #(println "clicked lorem!")} "Lorem"]
                                              [ui/secondary-action {:on-click #(println "clicked ipsum!")} "Ipsum"]]
                          :main-prop {:style {:position :relative}}
                          :main [simple-map]}
                         nav-params)

   [:div {:class-name "section"}
    [:h1 {:class-name "title-icon"} "Scenario C"
     [:a
      [m/Icon {} "favorite"]]]]
   [:hr]
   [:div {:class-name "section"}
    [:h1 {:class-name "large"}
     [:small "Increase in pregnancies coverage"]
     "25,238 (11.96%)"]
    [:p {:class-name "grey-text"} "to a total of 253,208(53.95%"]]
   [:div {:class-name "section"}
    [:h1 {:class-name "large"}
     [:small "Investment required"]
     "K 25,000,000"]]
   [:hr]
   [m/Fab {:class-name "btn-floating"} "domain"]
   [:div {:class-name "fade"}]
   [:div {:class-name "scroll-list"}
    [:div {:class-name "section"}
     [:div {:class-name "icon-list"}
      [m/Icon {} "domain"]
      [:div {:class-name "icon-list-text"}
       [:p {:class-name "strong"} "Build site with capacity on 50 pregnancies"]
       [:p {:class-name "grey-text"}  "K22,000,000"]
       [:p {:class-name "grey-text"}  "1,834 State House Rd, Nairobi, Kenya"]]]]
    [:hr]
    [:div {:class-name "section"}
     [:div {:class-name "icon-list"}
      [m/Icon {} "add"]
      [:div {:class-name "icon-list-text"}
       [:p {:class-name "strong"} "Increase capacity on 50 pregnancies"]
       [:p {:class-name "grey-text"}  "K1,200,000"]
       [:p {:class-name "grey-text"}  "Abdisamad Dispensary 725 Mara Rd, Nairobi, Kenya"]]]]
    [:hr]
    [:div {:class-name "section"}
     [:div {:class-name "icon-list"}
      [m/Icon {} "arrow_upward"]
      [:div {:class-name "icon-list-text"}
       [:p {:class-name "strong"} "Upgrade and increase capacity on 50 pregnancies"]
       [:p {:class-name "grey-text"}  "K1,800,000"]
       [:p {:class-name "grey-text"}  "348 Mbwara St, Nairobi, Kenya"]]]]]
   [:div {:class-name "fade inverted"}]
   [m/Button {:class-name "btn-create"} "Create new scenario from here"]])

(defn app
  []
  (let [page-params (subscribe [:page-params])]
    (fn []
      (let [section (:section @page-params)]
        (cond
          (= section :project) [demo-project]
          (= section :map) [demo-map]
          (= section :scenario) [demo-scenario]
          :else [demo-list])))))

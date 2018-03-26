(ns planwise.client.design.views
  (:require [re-frame.core :refer [subscribe]]
            [reagent.core :as r]
            [leaflet.core :as l]
            [planwise.client.mapping :as mapping]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]))

(def dialog-open (r/atom false))

(defn- project-tabs
  [{:keys [active] :or {active 0}}]
  [m/TabBar {:activeTabIndex active}
    [m/Tab "Lorem"]
    [m/Tab "Ipsum"]])

(def nav-params
  { :sections [[ui/section {:href "/_design" :className "active"} "Design"]
               [ui/section {:href "/_design/map"} "Map"]]
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
            [m/TextField {:label "Lorem"}]
            [m/TextField {:label "Ipsum"}]
            [m/Checkbox {} "dolor sit amet"]]]
        [m/GridCell {:span 6}
          [ui/panel {}
            [:pre {} "MAP"]]]
        [m/GridCell {:span 12}
          [:div.form-actions
            [m/Button {} "Continue"]]]]]])

(defn simple-map
  []
  (let [colors    [:red :blue :green :yellow :lime]
        position  (r/atom mapping/map-preview-position)
        zoom      (r/atom 3)
        points    (r/atom [])
        add-point (fn [lat lon] (swap! points conj {:lat lat
                                                    :lon lon
                                                    :weight 3
                                                    :color (first (shuffle colors))
                                                    :radius (+ 5 (rand-int 5))}))]
    (fn []
      [:div.map-container [l/map-widget {:zoom @zoom
                                         :position @position
                                         :on-position-changed #(reset! position %)
                                         :on-zoom-changed #(reset! zoom %)
                                         :on-click add-point
                                         :controls []}
                           mapping/default-base-tile-layer
                           [:point-layer {:points @points
                                          :style-fn #(select-keys % [:weight :radius :color])}]]])))

(defn demo-map
  []
  [ui/full-screen (merge {:tabs [project-tabs {:active 1}]
                          :main-prop {:style {:position :relative}}
                          :main [simple-map]}
                         nav-params)
   [:h1 "Lorem ipsum"]
   [:hr]
   [:h2 "dolor sit amet"]
   [:p 50000]])

(defn app
  []
  (let [page-params (subscribe [:page-params])]
    (fn []
      (let [section (:section @page-params)]
        (cond
          (= section :project) [demo-project]
          (= section :map) [demo-map]
          :else [demo-list])))))

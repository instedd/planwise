(ns planwise.client.design.views
  (:require [re-frame.core :refer [subscribe]]
            [reagent.core :as r]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]))

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
    :action (ui/main-action {:icon "add"})
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
      [project-card {:title "Amet"        :href "/_design/project?id=3"}]]])

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

(defn demo-map
  []
  [ui/fixed-width (merge {:tabs [project-tabs {:active 1}]} nav-params)
    [:pre "TODO MAP"]])

(defn app
  []
  (let [page-params (subscribe [:page-params])]
    (fn []
      (let [section (:section @page-params)]
        (cond
          (= section :project) [demo-project]
          (= section :map) [demo-map]
          :else [demo-list])))))

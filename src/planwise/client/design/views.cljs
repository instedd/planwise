(ns planwise.client.design.views
  (:require [reagent.core :as r]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]))

(defn- tabs
  []
  [m/TabBar {:activeTabIndex 0}
    [m/Tab "Lorem"]
    [m/Tab "Ipsum"]])

(defn card-list
  [props & children]
  [:section.card-list
    children])
    ; (seq (r/children (r/current-component)))])

(defn card
  [{:keys [primary title subtitle status]}]
  [:a {:className "card-item" :href "#"}
    [:div.card-primary primary]
    [:div.card-secondary
      [:h1 {} title]
      [:h2 {} subtitle]
      [:div.status {} status]]])

(defn project-card
  [{:keys [title]}]
  [card {:primary
           [:img {:src "http://via.placeholder.com/373x278"}]
         :title title}])

(defn demo-project-list
  []
  [card-list {}
    [project-card {:title "Lorem ipsum"}]
    [project-card {:title "Dolor sit"}]
    [project-card {:title "Amet"}]])

(defn app
  []
  [ui/fixed-width
    { :sections [ui/section {:href "#"} "Section1"]
      :account [ui/account {:name "John Doe" :on-signout #(println "Sign out")}]
      :title "Planwise"
      :tabs [tabs]
      :action (ui/main-action {:icon "add"})
      :footer [ui/footer]}
    ; [m/TextField {:label "Lorem"}]
    ; [m/Fab {} "favorite"]
    ; [m/Button {} "I'm a button"]
    [demo-project-list]])
    ; [ui/panel {:z 2} "Hi"]])

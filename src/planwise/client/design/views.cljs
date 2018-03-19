(ns planwise.client.design.views
  (:require [reagent.core :as r]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.ui.common :as ui]))

(defn- tabs
  []
  [m/TabBar {:activeTabIndex 0}
    [m/Tab "Lorem"]
    [m/Tab "Ipsum"]])

(defn project-card
  [{:keys [title]}]
  [ui/card {:primary [:img {:src "http://via.placeholder.com/373x278"}]
            :title title}])

(defn demo-project-list
  []
  [ui/card-list {}
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

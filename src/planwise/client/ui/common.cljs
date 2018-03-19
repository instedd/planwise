(ns planwise.client.ui.common
  (:require [reagent.core :as r]
            [planwise.client.ui.rmwc :as m]))

(defn fixed-width
  [{:keys [sections account title tabs action footer]} & children]
  [:div.layout.fixed-width
    [m/Toolbar
      [m/ToolbarRow {:id "top-row"}
        (into [m/ToolbarSection {:id "section-row" :alignStart true}] sections)
        [m/ToolbarSection {:alignEnd true} account]]
      [m/ToolbarRow {:id "title-row"}
        [m/ToolbarTitle title]]
      (if tabs [m/ToolbarRow {:id "tabs-row"} tabs])
      action]
    (into [:main] children)
    footer])

(defn full-screen
  [{:keys [sections account title tabs action footer main-prop main]} & children]
  [:div.layout.full-screen
    [m/Toolbar
      [m/ToolbarRow {:id "top-row"}
        (into [m/ToolbarSection {:id "section-row" :alignStart true}] sections)
        [m/ToolbarSection {:alignEnd true} account]]
      [m/ToolbarRow {:id "title-row"}
        [m/ToolbarTitle title]]
      (if tabs [m/ToolbarRow {:id "tabs-row"} tabs])
      action]
    [:main main-prop main]
    (into [:aside] children)
    footer])

(defn footer
  []
  [:footer.mdc-theme--text-disabled-on-background "Version: X.Y.Z"])

(defn main-action
  [{:keys [icon]}]
  [m/Fab {:id "main-action" :class "MyClass"} icon])

(defn section
  [props label]
  [:a props label])

(defn account
  [{:keys [name on-signout]}]
  (let [open (r/atom false)]
    (fn []
      [m/MenuAnchor
        [m/Menu {:anchorCorner "bottomStart" :open @open :onClose #(reset! open false)}
          [m/MenuItem {:on-click on-signout} "Sign out"]]
        [:a {:id "account-menu" :href "#" :onClick #(reset! open true)}
          name]])))

(defn panel
  [{:keys [z] :or {z 2}} & children]
  (into [m/Elevation {:z z :className "panel"}] children))

(defn card-list
  [props & children]
  (into [:section.card-list] children))

(defn card
  [{:keys [href primary title subtitle status]}]
  [:a {:className "card-item" :href href}
    [:div.card-primary primary]
    [:div.card-secondary
      [:h1 {} title]
      [:h2 {} subtitle]
      [:div.status {} status]]])

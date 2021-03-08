(ns planwise.client.ui.common
  (:require [reagent.core :as r]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.ui.rmwc :as m]))

(defn- secondary-actions-menu
  [_ secondary-actions]
  (let [open (r/atom false)]
    (fn [_ secondary-actions]
      [m/MenuAnchor
       (into [m/Menu {:anchorCorner "bottomStart" :open @open :onClose #(reset! open false)}]
             secondary-actions)
       [:a {:id "secondary-actions-handle" :href "#" :onClick #(reset! open true)} [m/Icon {} "more_vert"]]])))

(defn- header
  [{:keys [sections account title tabs action secondary-actions]}]
  [m/Toolbar
   [m/ToolbarRow {:id "top-row"}
    (into [m/ToolbarSection {:id "section-row" :alignStart true}] sections)
    [m/ToolbarSection {:alignEnd true} account]]
   [m/ToolbarRow {:id "title-row"}
    [icon "logo2"]
    [m/ToolbarTitle title]]
   (if (or tabs secondary-actions)
     [m/ToolbarRow {:id "tabs-row"}
      tabs
      (if secondary-actions [secondary-actions-menu {} secondary-actions])])
   action])

(defn fixed-width
  [{:keys [sections account title tabs action footer secondary-actions]} & children]
  [:div.layout.fixed-width
   [header {:sections sections :account account :title title :tabs tabs :action action :secondary-actions secondary-actions}]
   (into [:main] children)
   footer])

(defn full-screen
  [{:keys [sections account title tabs action footer main-prop main secondary-actions]} & children]
  [:div.layout.full-screen
   [header {:sections sections :account account :title title :tabs tabs :action action :secondary-actions secondary-actions}]
   [:main main-prop main]
   (into [:aside {:id "sidebar"}] children)
   footer])

(defn footer
  ([]
   (footer "X.Y.Z"))
  ([version]
   [:footer.mdc-theme--text-disabled-on-background (str "Version: " version)]))

(defn main-action
  [{:keys [icon on-click]}]
  [m/Fab {:id "main-action" :on-click on-click :icon icon} icon])

(defn secondary-action
  [{:keys [on-click]} content]
  [m/MenuItem {:on-click on-click} content])

(defn section
  [{:keys [active] :as props} label]
  (let [props (dissoc props :active)]
    [:a
     (merge props {:class-name (if active "active")})
     label]))

(defn account
  [{:keys [name on-signout]}]
  (let [open (r/atom false)]
    (fn []
      [m/MenuAnchor
       [m/Menu {:anchorCorner "bottomStart" :open @open :onClose #(reset! open false)}
        [m/MenuItem {:on-click on-signout} "Sign out"]]
       [:a {:id "account-menu" :href "#" :onClick #(reset! open true)}
        name [:i.material-icons.icon-dropdown "keyboard_arrow_down"]]])))

(defn panel
  [{:keys [z] :or {z 2}} & children]
  (into [m/Elevation {:z z :className "panel"}] children))

(defn card-list
  [props & children]
  (into [:section.card-list props] children))

(defn card
  [{:keys [href primary title subtitles status action-button]}]
  [:a {:className "card-item" :href href}
   [:div.card-primary primary]
   [:div.card-secondary
    [:h1 {} title]
    (into [:<>] (map (fn [a] [:h2 {} a]) subtitles))
    [:div.status {} status]
    (when action-button
      [:div.actions action-button])]])

(defn sortable-table-header
  [{:keys [sorted order align class] :as props} title]
  (let [new-props (-> props
                      (dissoc :sorted :order :align :class)
                      (assoc :class (concat class [:rmwc-data-table__cell
                                                   :rmwc-data-table__head-cell
                                                   :rmwc-data-table__head-cell--sortable
                                                   (if sorted :rmwc-data-table__head-cell--sorted)
                                                   (if (= order :asc) :rmwc-data-table__head-cell--sorted-ascending)
                                                   (if (= order :desc) :rmwc-data-table__head-cell--sorted-descending)
                                                   (if (= align :left) :rmwc-data-table__cell--align-start)
                                                   (if (= align :right) :rmwc-data-table__cell--align-end)])))
        icon [:i.rmwc-icon.material-icons.rmwc-data-table__sort-icon "arrow_upward"]]
    [:th new-props
     (if (= align :right) icon)
     title
     (if (= align :left) icon)]))

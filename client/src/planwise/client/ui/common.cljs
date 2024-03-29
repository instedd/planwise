(ns planwise.client.ui.common
  (:require [reagent.core :as r]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.ui.rmwc :as m]))



(defn close-button
  [{:keys [on-click]}]
  [:button.icon-button {:on-click on-click} [m/Icon "close"]])

(defn- secondary-actions-menu
  [_ secondary-actions]
  (let [open (r/atom false)]
    (fn [_ secondary-actions]
      [m/MenuAnchor
       (into [m/Menu
              {:anchorCorner "bottomStart"
               :open         @open
               :onClose      #(reset! open false)}]
             secondary-actions)
       [:a#secondary-actions-handle
        {:href    "#"
         :onClick #(swap! open not)}
        [m/Icon {} "more_vert"]]])))

(defn- header
  [{:keys [sections account title title-actions tabs action secondary-actions]}]
  [m/Toolbar
   [m/ToolbarRow {:id "top-row"}
    (into [m/ToolbarSection {:id "section-row" :alignStart true}] sections)
    [m/ToolbarSection {:alignEnd true} account]]
   [m/ToolbarRow {:id "title-row"}
    [icon "logo2"]
    [m/ToolbarTitle title]
    (when title-actions [secondary-actions-menu {} title-actions])]
   (if (or tabs secondary-actions)
     [m/ToolbarRow {:id "tabs-row"}
      tabs
      (if secondary-actions [secondary-actions-menu {} secondary-actions])])
   action])

(defn fixed-width
  [{:keys [sections account title tabs action footer secondary-actions]} & children]
  [:div.layout.fixed-width
   (into [:main] children)
   footer
   [header {:sections sections :account account :title title :tabs tabs :action action :secondary-actions secondary-actions}]])

(defn full-screen
  [{:keys [footer main sidebar-prop] :as props} & children]
  (let [header-props (dissoc props :main :main-prop :sidebar-prop :footer)]
    [:div.layout.full-screen
     [:main main]
     (into [:aside.sidebar sidebar-prop] children)
     footer
     [header header-props]]))

(defn footer
  ([]
   (footer "X.Y.Z"))
  ([version]
   [:footer.mdc-theme--text-disabled-on-background (str "Version: " version)]))

(defn main-action
  [{:keys [icon on-click]}]
  [m/Fab {:id "main-action" :on-click on-click :icon icon} icon])

(defn menu-item
  [{:keys [on-click disabled icon]} & children]
  (into [m/MenuItem {:className (when disabled "mdc-list-item--disabled")
                     :on-click  #(when-not disabled (on-click))}
         (when icon [m/ListItemGraphic icon])]
        children))

(defn link-menu-item
  [{:keys [url icon]} & children]
  (into [:a.mdc-list-item
         {:role      "menuitem"
          :tab-index 0
          :target    "_blank"
          :href      url}
         (when icon [m/ListItemGraphic icon])]
        children))

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
    [:h1 {:title title} title]
    (into [:<>] (map (fn [a] [:h2 {} a]) subtitles))
    [:div.status {} status]
    (when action-button
      [:div.actions action-button])]])

(defn sortable-table-header
  [{:keys [sorted order align class tooltip] :as props} title]
  (let [new-props (-> props
                      (dissoc :sorted :order :align :class :tooltip)
                      (assoc :class (concat class [:rmwc-data-table__cell
                                                   :rmwc-data-table__head-cell
                                                   :rmwc-data-table__head-cell--sortable
                                                   (if sorted :rmwc-data-table__head-cell--sorted)
                                                   (if (= order :asc) :rmwc-data-table__head-cell--sorted-ascending)
                                                   (if (= order :desc) :rmwc-data-table__head-cell--sorted-descending)
                                                   (if (= align :left) :rmwc-data-table__cell--align-start)
                                                   (if (= align :right) :rmwc-data-table__cell--align-end)
                                                   (if (some? tooltip) :has-tooltip)])))
        icon [:i.rmwc-icon.material-icons.rmwc-data-table__sort-icon "arrow_upward"]]
    [:th new-props
     [:p
      (if (= align :right) icon)
      title
      (if (= align :left) icon)]
     (if (some? tooltip) [:div.tooltip tooltip])]))

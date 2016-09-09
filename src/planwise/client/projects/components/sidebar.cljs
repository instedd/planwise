(ns planwise.client.projects.components.sidebar
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [planwise.client.components.progress-bar :as progress-bar]
            [planwise.client.components.filters :as filters]
            [planwise.client.projects.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [planwise.client.styles :as styles]))

(defn- demographic-stat [title value]
  [:div.stat
   [:div.stat-title title]
   [:div.stat-value value]])

(defn- route-by-tab-name [tab-name project-id]
  (case tab-name
    :demographics    (routes/project-demographics {:id project-id})
    :facilities      (routes/project-facilities {:id project-id})
    :transport       (routes/project-transport {:id project-id})))

(defn- next-and-back-buttons [tab-name project-id]
  (let [tabs [:demographics :facilities :transport]
        has-next? (not= (last tabs) tab-name)
        has-back? (not= (first tabs) tab-name)
        next (second (drop-while #(not= tab-name %) tabs))
        back (last (take-while #(not= tab-name %) tabs))]
    [:div.nav-buttons {:class (if (and has-back? has-next?) "both" "just-one")}
      (when has-back?
        [:div.nav-button.prev {:on-click #(accountant/navigate! (route-by-tab-name back project-id))}
         (icon :key-arrow-left "icon-small")
         [:span.prev-button-text "Prev"]])
      (when has-next?
        [:div.nav-button.next {:on-click #(accountant/navigate! (route-by-tab-name next project-id))}
         [:span.next-button-text "Next"]
         (icon :key-arrow-right "icon-small")])]))

(defn- demographics-filters []
  (let [current-project (subscribe [:projects/current-data])]
    (fn []
      (let [population (:region-population @current-project)
            area (:region-area-km2 @current-project)
            density (/ population area)]
        [:div.sidebar-filters
         [:div.filter-info
          ;; [:p "Filter here the population you are analyzing."]
          [:div.demographic-stats
           (demographic-stat "Area" [:span (utils/format (int area)) " km" [:sup 2]])
           (demographic-stat "Density" [:span (utils/format density) " /km" [:sup 2]])
           (demographic-stat "Total population" (utils/format population))]
          [:span.small
           "Demographic data source: "
           [:a {:href "http://www.worldpop.org.uk/" :target "attribution"} "WorldPop"]
           " (Geodata Institute of the University of Southampton) / "
           [:a {:href "https://creativecommons.org/licenses/by/4.0/" :target "attribution"} "CC BY"]]]]))))

(defn- facility-filters []
  (let [facility-types (subscribe [:filter-definition :facility-type])
        facility-ownerships (subscribe [:filter-definition :facility-ownership])
        facility-services (subscribe [:filter-definition :facility-service])
        filters (subscribe [:projects/facilities :filters])
        filter-stats (subscribe [:projects/facilities :filter-stats])]
    (fn []
      (let [filter-count (:count @filter-stats)
            filter-total (:total @filter-stats)
            toggle-cons-fn (fn [field]
                             #(dispatch [:projects/toggle-filter :facilities field %]))]
        [:div.sidebar-filters
         [:div.filter-info
          [:p "Select the facilities that are satisfying the demand you are analyzing."]
          [:p
           [:div.small "Target / Total Facilities"]
           [:div (str filter-count " / " filter-total)]
           [progress-bar/progress-bar filter-count filter-total]]]

         [:fieldset
          [:legend "Type"]
          (filters/filter-checkboxes
           {:options @facility-types
            :value (:type @filters)
            :toggle-fn (toggle-cons-fn :type)})]

         #_[:fieldset
            [:legend "Ownership"]
            (filters/filter-checkboxes
             {:options @facility-ownerships
              :value (:ownership @filters)
              :toggle-fn (toggle-cons-fn :ownership)})]

         #_[:fieldset
            [:legend "Services"]
            (filters/filter-checkboxes
             {:options @facility-services
              :value (:services @filters)
              :toggle-fn (toggle-cons-fn :services)})]
         ]))))

(defn- transport-filters []
  (let [transport-time (subscribe [:projects/transport-time])]
    (fn []
      [:div.sidebar-filters
       [:div.filter-info
        [:p "Indicate here the acceptable travel times to facilities. We will
        use that to calculate who already has access to the services that you
        are analyzing."]]

       [:fieldset
        [:legend "By car"]
        [rc/single-dropdown
         :choices (:time db/transport-definitions)
         :label-fn :name
         :on-change #(dispatch [:projects/set-transport-time %])
         :model transport-time]
        (icon :car)]])))

(defn sidebar-section [selected-tab]
  (let [current-project (subscribe [:projects/current-data])
        wizard-mode-on (subscribe [:projects/wizard-mode-on])]
    [:aside (condp = selected-tab
              :demographics
              [demographics-filters]
              :facilities
              [facility-filters]
              :transport
              [transport-filters])
     (when @wizard-mode-on
       (next-and-back-buttons selected-tab (:id @current-project)))]))

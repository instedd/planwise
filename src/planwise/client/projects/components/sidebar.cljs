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

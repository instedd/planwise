(ns planwise.client.current-project.components.sidebar
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.components.progress-bar :as progress-bar]
            [planwise.client.components.filters :as filters]
            [planwise.client.current-project.db :as db]
            [planwise.client.styles :as styles]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [planwise.client.config :as config]
            [planwise.client.mapping :refer [calculate-demand-for-admin-level?]]))


(defn- demographic-stat [title value]
  [:div.stat
   [:div.stat-title title]
   [:div.stat-value value]])

(defn- route-by-tab-name [tab-name project-id]
  (case tab-name
    :demographics    (routes/project-demographics {:id project-id})
    :facilities      (routes/project-facilities {:id project-id})
    :transport       (routes/project-transport {:id project-id})))

;; TODO: refactor this to re-use header/project-tab-items
(defn- next-and-back-buttons [tab-name project-id]
  (let [tabs [:demographics :facilities :transport]
        has-next? (not= (last tabs) tab-name)
        has-back? (not= (first tabs) tab-name)
        next (second (drop-while #(not= tab-name %) tabs))
        back (last (take-while #(not= tab-name %) tabs))]
    ;; TODO: change these navigations to re-frame events
    [:div.nav-buttons
      (when has-back?
        [:button.secondary {:on-click #(accountant/navigate! (route-by-tab-name back project-id))}
         (icon :key-arrow-left "icon-small")
         "Prev"])
      (when has-next?
        [:button.primary {:on-click #(accountant/navigate! (route-by-tab-name next project-id))}
         "Next"
         (icon :key-arrow-right "icon-small")])]))

(defn- demographics-filters []
  (let [current-project (subscribe [:current-project/current-data])]
    (fn []
      (let [population (:region-population @current-project)
            area (:region-area-km2 @current-project)
            density (if (pos? area) (/ population area) 0)]
        [:div.sidebar-filters
         [:div.filter-info
          ;; [:p "Filter here the population you are analyzing."]
          [:div.demographic-stats
           (demographic-stat "Area" [:span (utils/format-number (int area)) " km" [:sup 2]])
           (demographic-stat "Density" [:span (utils/format-number density) " /km" [:sup 2]])
           (demographic-stat "Total population" (utils/format-number (int population)))]
          [:span.small
           "Demographic data source: "
           [:a {:href "http://www.worldpop.org.uk/" :target "attribution"} "WorldPop"]
           " (Geodata Institute of the University of Southampton) / "
           [:a {:href "https://creativecommons.org/licenses/by/4.0/" :target "attribution"} "CC BY"]]]]))))

(defn- facility-filters []
  (let [facility-types (subscribe [:current-project/filter-definition :facility-type])
        ;; facility-ownerships (subscribe [:current-project/filter-definition :facility-ownership])
        ;; facility-services (subscribe [:current-project/filter-definition :facility-service])
        filters (subscribe [:current-project/facilities :filters])
        stats (subscribe [:current-project/facilities :stats])
        dataset-sub (subscribe [:current-project/dataset])
        read-only? (subscribe [:current-project/read-only?])]
    (fn []
      (let [filter-count (:facilities-targeted @stats)
            filter-total (:facilities-total @stats)
            toggle-cons-fn (fn [field]
                             #(dispatch [:current-project/toggle-filter :facilities field %]))]
        [:div.sidebar-filters
         [:div.filter-info
          (if @read-only?
           [:p "Facilities used to calculate the existing coverage."]
           [:p "Select the types of facility to include in your analysis. We will use those to calculate the existing coverage."])
          [:div
           [:div.small "Target / Total Facilities"]
           [:div (str filter-count " / " filter-total)]
           [progress-bar/progress-bar filter-count filter-total]]
          [:div.small.facilities-stats (when-let [dataset (asdf/value @dataset-sub)]
                                         (str "Facilities from dataset "(:name dataset)))]]

         [:fieldset
          [:legend "Type"]
          (filters/filter-checkboxes
           {:options @facility-types
            :value (:type @filters)
            :disabled @read-only?
            :toggle-fn (toggle-cons-fn :type)
            :decoration-fn (fn [{colour :colour, :as opt}] [:span.filter-colour {:style {"backgroundColor" colour}}])})
          [:hr]
          [:div {:title "These facilities are too far from the closest road and are not being evaluated for coverage."}
           [:span.filter-colour {:style {"backgroundColor" styles/invalid-facility-type}}]
           [:label "Not in road network"]]]

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
              :toggle-fn (toggle-cons-fn :services)})]]))))

(defn- transport-filters []
  (let [transport-time (subscribe [:current-project/transport-time])
        read-only? (subscribe [:current-project/read-only?])
        satisfied-demand (subscribe [:current-project/satisfied-demand])
        current-project (subscribe [:current-project/current-data])
        facilities-capacity config/facilities-default-capacity]
    (fn []
      [:div.sidebar-filters
       [:div.filter-info
        (if @read-only?
         [:p "Acceptable one-way travel time to facilities, used to calculate who
         already has access to the services being analyzed."]
         [:p "Indicate here the acceptable one-way travel time to facilities. We will
         use that to calculate who already has access to the services that you
         are analyzing."])
        (when (calculate-demand-for-admin-level? (:region-admin-level @current-project))
         (let [satisfied (or @satisfied-demand 0)
               total (:region-population @current-project)]
          [:div
           [:div.small "With access / Total Population"]
           [:div (str (utils/format-number satisfied) " / " (utils/format-number total))]
           [progress-bar/progress-bar satisfied total]
           [:div.small.facilities-stats (str "Assuming that each facility can provide coverage for a population of " (utils/format-number facilities-capacity))]]))]

       [:fieldset
        [:legend "By car"]
        [rc/single-dropdown
         :choices (:time db/transport-definitions)
         :label-fn :name
         :disabled? read-only?
         :on-change #(dispatch [:current-project/set-transport-time %])
         :model transport-time]
        (icon :car)]])))

(defn sidebar-section [selected-tab]
  (let [current-project (subscribe [:current-project/current-data])
        wizard-mode-on (subscribe [:current-project/wizard-mode-on])]
    [:aside
     (case selected-tab
       :demographics [demographics-filters]
       :facilities   [facility-filters]
       :transport    [transport-filters])
     (when @wizard-mode-on
       (next-and-back-buttons selected-tab (:id @current-project)))]))

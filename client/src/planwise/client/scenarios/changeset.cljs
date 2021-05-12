(ns planwise.client.scenarios.changeset
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.ui.common :as ui]
            [planwise.client.scenarios.edit :as edit]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]
            [planwise.common :as common]))

(def action-icons
  {"create-provider" "domain"
   "upgrade-provider" "arrow_upward"
   "increase-provider" "add"})

(defn- action-description
  [{:keys [capacity-unit demand-unit] :as props} {:keys [change satisfied-demand] :as provider}]
  (let [formatted-capacity (utils/format-number (:capacity change))
        formatted-coverage (utils/format-number satisfied-demand)]
    (case (:action change)
      "create-provider"   (str "New build with " formatted-capacity " " capacity-unit " to serve " formatted-coverage " " demand-unit)
      "upgrade-provider"  (str "Upgraded and increased capacity on " formatted-capacity " " capacity-unit " to serve " formatted-coverage " " demand-unit)
      "increase-provider" (str "Increased capacity on " formatted-capacity " " capacity-unit " to serve " formatted-coverage " " demand-unit))))

(defn- action-description-with-investment
  [props {:keys [change] :as provider}]
  (let [investment           (:investment change)
        formatted-investment (when (pos? investment) (str " at " (utils/format-currency investment)))]
    (str (action-description props provider) formatted-investment)))

(defn- provider-icon
  [{:keys [change] :as provider}]
  [m/Icon {} (get action-icons (:action change))])

(defn- changeset-row
  [props {:keys [name change] :as provider}]
  (let [action (:action change)]
    [:div
     [:div.section.changeset-row {:on-mouse-over #(dispatch [:scenarios.map/select-provider provider])
                                  :on-mouse-out  #(dispatch [:scenarios.map/unselect-provider provider])
                                  :on-click      #(dispatch [:scenarios/open-changeset-dialog provider])}
      [:div.icon-list
       [m/Icon {} (get action-icons action)]
       [:div.icon-list-text
        [:p.strong name]
        [:p.grey-text
         (action-description-with-investment props provider)]]]]
     [:hr]]))

(defn listing-component
  [props providers]
  [:div.scroll-list
   (map (fn [provider] [changeset-row (merge props {:key (str "provider-action" (:id provider))}) provider])
        providers)])

(defn- suggestion-row
  [{:keys [demand-unit capacity-unit] :as props} {:keys [coverage action-capacity ranked name] :as suggestion}]
  [:div
   [:div.section.changeset-row {:on-mouse-over #(dispatch [:scenarios.map/select-suggestion suggestion])
                                :on-mouse-out  #(dispatch [:scenarios.map/unselect-suggestion suggestion])
                                :on-click      #(dispatch [:scenarios/edit-suggestion suggestion])}
    [:div.icon-list
     [m/Icon {} (get action-icons "create-provider")]
     [:div.icon-list-text
      [:p.strong (if name name (str "Suggestion " ranked))]
      ; coverage is nil when requesting suggestions to improve existing provider
      ; and it is not nil when requesting suggestions for new providers
      [:p.grey-text (str "Required Capacity: " (utils/format-number (Math/ceil action-capacity)) " " capacity-unit)]
      (when (some? coverage)
        [:p.grey-text (str " Coverage: " (utils/format-number coverage) " " demand-unit)])]]]
   [:hr]])

(defn suggestion-listing-component
  [props suggestions]
  [:div.scroll-list.suggestion-list
   (map (fn [suggestion] [suggestion-row (merge props {:key (str "suggestion-action" (:name suggestion) (:ranked suggestion))}) suggestion])
        suggestions)])

(defn- changeset-table-row
  [props {:keys [name change] :as provider}]
  [:tr
   [:td.col-action-icon [provider-icon provider]]
   [:td.col-action-name name]
   [:td.col-action-description (action-description-with-investment props provider)]])

(defn table-component
  [props providers]
  [:div..scenarios-content
   [:table
    [:thead
     [:tr
      [:th.col-action-icon]
      [:th.col-action-name "Name"]
      [:th.col-action-description "Action"]]]
    [:tbody
     (map (fn [provider]
            ^{:key (str "table-provider-action" (:id provider))} [changeset-table-row props provider])
          providers)]]])

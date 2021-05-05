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

(def action-texts
  {"create-provider" "New build with"
   "upgrade-provider" "Upgraded and increased capacity on"
   "increase-provider" "Increased capacity on"})

(defn- changeset-row
  [{:keys [capacity-unit demand-unit] :as props} {:keys [name change satisfied-demand] :as provider}]
  (let [action (:action change)]
    [:div
     [:div {:class-name    "section changeset-row"
            :on-mouse-over #(dispatch [:scenarios.map/select-provider provider])
            :on-mouse-out  #(dispatch [:scenarios.map/unselect-provider provider])
            :on-click      #(dispatch [:scenarios/open-changeset-dialog provider])}
      [:div {:class-name "icon-list"}
       [m/Icon {} (get action-icons action)]
       [:div {:class-name "icon-list-text"}
        [:p {:class-name "strong"} name]
        [:p {:class-name "grey-text"}
         (str (get action-texts action)
              " "
              (utils/format-number (:capacity change))
              " "
              capacity-unit
              " to serve "
              (utils/format-number satisfied-demand)
              " "
              demand-unit
              (when (pos? (:investment change))
                (str " at " (utils/format-currency (:investment change)))))]]]]
     [:hr]]))

(defn listing-component
  [props providers]
  [:div {:class-name "scroll-list"}
   (map (fn [provider] [changeset-row (merge props {:key (str "provider-action" (:id provider))}) provider])
        providers)])

(defn- suggestion-row
  [{:keys [demand-unit capacity-unit] :as props} {:keys [coverage action-capacity ranked name] :as suggestion}]
  [:div
   [:div {:class-name    "section changeset-row"
          :on-mouse-over #(dispatch [:scenarios.map/select-suggestion suggestion])
          :on-mouse-out  #(dispatch [:scenarios.map/unselect-suggestion suggestion])
          :on-click      #(dispatch [:scenarios/edit-suggestion suggestion])}
    [:div {:class-name "icon-list"}
     [m/Icon {} (get action-icons "create-provider")]
     [:div {:class-name "icon-list-text"}
      [:p {:class-name "strong"} (if name name (str "Suggestion " ranked))]
      ; coverage is nil when requesting suggestions to improve existing provider
      ; and it is not nil when requesting suggestions for new providers
      [:p {:class-name "grey-text"} (str "Required Capacity: " (utils/format-number (Math/ceil action-capacity)) " " capacity-unit)]
      (when (some? coverage)
        [:p {:class-name "grey-text"} (str " Coverage: " (utils/format-number coverage) " " demand-unit)])]]]
   [:hr]])

(defn suggestion-listing-component
  [props suggestions]
  [:div {:class-name "scroll-list suggestion-list"}
   (map (fn [suggestion] [suggestion-row (merge props {:key (str "suggestion-action" (:name suggestion) (:ranked suggestion))}) suggestion])
        suggestions)])

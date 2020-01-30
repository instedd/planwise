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
            [planwise.client.ui.rmwc :as m]))

(def action-icons
  {"create-provider" "domain"
   "upgrade-provider" "arrow_upward"
   "increase-provider" "add"})

(defn- changeset-row
  [props {:keys [name change] :as provider}]
  [:div
   [:div {:class-name "section changeset-row"
          :on-click #(dispatch [:scenarios/open-changeset-dialog provider])}
    [:div {:class-name "icon-list"}
     [m/Icon {} (get action-icons (:action change))]
     [:div {:class-name "icon-list-text"}
      [:p {:class-name "strong"} name]
      [:p {:class-name "grey-text"}  (str "K " (utils/format-number (:investment change)))]]]]
   [:hr]])

(defn- listing-component
  [providers]
  [:div {:class-name "scroll-list"}
   (map (fn [provider] [changeset-row {:key (str "provider-action" (:id provider))} provider])
        providers)])

(defn- suggestion-row
  [props {:keys [coverage action-capacity ranked name] :as suggestion}]
  [:div
   [:div {:class-name "section changeset-row"
          :on-mouse-over #(dispatch [:scenarios.map/select-suggestion suggestion])
          :on-mouse-out  #(dispatch [:scenarios.map/unselect-suggestion suggestion])}
    [:div {:class-name "icon-list"}
     [m/Icon {} (get action-icons "create-provider")]
     [:div {:class-name "icon-list-text"}
      [:p {:class-name "strong"} (if name name (str "Suggestion " ranked))]
      [:p {:class-name "grey-text"}  (str "Required Capacity: " action-capacity " Coverage: " coverage)]]]]
   [:hr]])

(defn- suggestion-listing-component
  [suggestions]
  [:div {:class-name "scroll-list suggestion-list"}
   (map (fn [suggestion] [suggestion-row {:key (str "suggestion-action" (:name suggestion) (:ranked suggestion))} suggestion])
        suggestions)])

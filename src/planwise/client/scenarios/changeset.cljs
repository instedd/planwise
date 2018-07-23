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

(defn- changeset-row
  [props prov-index {:keys [action investment]}]

  [:div
   [:div {:class-name "section changeset-row"
          :on-click #(dispatch [:scenarios/open-changeset-dialog (:key props)])}
    [:div {:class-name "icon-list"}
     [m/Icon {} "domain"]
     [:div {:class-name "icon-list-text"}
      [:p {:class-name "strong"} (str "Create new provider " prov-index)]
      [:p {:class-name "grey-text"}  (str "K " (utils/format-number investment))]]]]
   [:hr]])

(defn- listing-component
  [providers]
  [:div {:class-name "scroll-list"}
   (map (fn [[idx {:keys [provider index]}]] [changeset-row {:key idx} index provider])
        (map-indexed vector providers))])

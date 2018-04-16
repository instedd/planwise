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
  [props index site]
  [:div
   [:div {:class-name "section changeset-row"
          :on-click #(dispatch [:scenarios/open-changeset-dialog index])}
    [:div {:class-name "icon-list"}
     [m/Icon {} "domain"]
     [:div {:class-name "icon-list-text"}
      [:p {:class-name "strong"} "Create new site"]
      [:p {:class-name "grey-text"}  (str "K " (:investment site))]
      [:p {:class-name "grey-text"}  "1,834 State House Rd, Nairobi, Kenya"]]]]
   [:hr]])

(defn- listing-component
  [scenario]
  [:div {:class-name "scroll-list"}
   (map-indexed (fn [i site] [changeset-row {:key i} i site])
                (:changeset scenario))])

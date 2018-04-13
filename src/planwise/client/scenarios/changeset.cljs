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

(defn- site-card
  [props index site]
  [:div
   [m/Button {:on-click #(dispatch [:scenarios/open-changeset-dialog index])}
    "Edit Site"]
   [:div [:h1 "Create new site"]
    [:h2 (str "K " (:investment site))]]])

(defn- listing-component
  [scenario]
  [:div {}
   (map-indexed (fn [i site] [site-card {:key i} i site])
                (:changeset scenario))])

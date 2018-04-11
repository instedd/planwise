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
  [site]
  [:div
   [edit/site-button site]
   [ui/card {:title (:create-site/action site)
             :subtitle (str "K " (:investment site))}]])

(defn- sites-list
  [changeset]
  [ui/card-list {}
   (for [site changeset]
     [site-card site])])

(defn- listing-component
  []
  (let [current-scenario (subscribe [:scenarios/current-scenario])]
    [sites-list (:changeset @current-scenario)]))
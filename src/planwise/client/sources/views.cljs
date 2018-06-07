(ns planwise.client.sources.views
  (:require [re-frame.core :as rf]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; Sources list

(defn no-sources-view
  []
  [:div.empty-list-container
   [:div.empty-list
    [common/icon :box]
    [:p "You have no sources yet"]]])

(defn sources-page
  []
  [ui/fixed-width (common2/nav-params)
   [no-sources-view]])

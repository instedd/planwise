(ns planwise.client.sources.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.components.common :as common]))

;; ----------------------------------------------------------------------------
;; Sources list

(defn empty-list-view
  []
  [:div.empty-list-container
   [:div.empty-list
    [common/icon :box]
    [:p "You have no sources yet"]]])

(defn source-card
  [props source]
  (let [name (:name source)]
    [ui/card {:title name}]))

(defn list-view
  [sources]
  (if (empty? sources)
    [empty-list-view]
    [ui/card-list {:class "dataset-list"}
     (for [source sources]
       [source-card {:key (:id source)} source])]))

(defn sources-page
  []
  (let [sources-sub (rf/subscribe [:sources/list])
        sources     (asdf/value @sources-sub)]

    (when (asdf/should-reload? @sources-sub)
      (rf/dispatch [:sources/load]))

    (if (nil? sources)
      [common2/loading-placeholder]
      ;else
      [ui/fixed-width (common2/nav-params)
       [list-view sources]])))

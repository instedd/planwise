(ns viewer.hud
  (:require [reagent.core :as reagent]
            [goog.string :as gstring]
            [goog.string.format]))

(defn format-coord [x]
  (gstring/format "%.4f" x))

(defn coords-and-info [{:keys [lat lon zoom node-id on-reset-view]}]
  [:div.actions
   [:div.left
    [:button {:on-click (fn [_]
                          (when on-reset-view
                            (on-reset-view)))}
     "Reset view"]]
   [:div.center
    (when node-id [:span.small "Node ID " node-id])]
   [:div.right
    [:span.small "Lat " (format-coord lat) " Lon " (format-coord lon) " Zoom " zoom]]])

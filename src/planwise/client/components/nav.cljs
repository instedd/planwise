(ns planwise.client.components.nav
  (:require [planwise.client.components.common :as components]))

;; Navigation components

(defn- li-menu-item [{:keys [selected item href title icon]}]
  (let [is-selected? (or (= selected item)
                         (item selected))]
    [:li {:class (when is-selected? "active")}
     [:a {:href href}
      (some-> icon (components/icon "icon-small"))
      title]]))

(defn ul-menu [items selected]
  [:ul (map-indexed (fn [idx item]
                      [li-menu-item (assoc item
                                           :key idx
                                           :selected selected)])
                    items)])

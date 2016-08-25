(ns planwise.client.components.nav
  (:require [planwise.client.components.common :as components]))

;; Navigation components

(defn- li-menu-item [{:keys [selected item href title icon wizard-state]}]
  (let [is-selected? (or (= selected item)
                         (item selected))]
    [:li {:class (str
                  (when is-selected? "active ")
                  (when wizard-state (name wizard-state)))}
     [:a {:href href}
      (some-> icon (components/icon "icon-small"))
      title]]))

(defn ul-menu [items selected]
  [:ul (map-indexed (fn [idx item]
                      [li-menu-item (assoc item
                                           :key idx
                                           :selected selected)])
                    items)])

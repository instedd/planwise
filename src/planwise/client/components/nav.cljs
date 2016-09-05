(ns planwise.client.components.nav
  (:require [planwise.client.components.common :as components]))

;; Navigation components

(defn- li-menu-item [{:keys [selected item href title icon tab-number wizard-state]}]
  (let [is-selected? (or (= selected item)
                         (item selected))]
    [:li {:class (str
                  (when is-selected? "active ")
                  (when wizard-state (str (name wizard-state) " wizard")))}
     [:a {:href href}
      (some-> icon (components/icon "icon-small"))
      (when (and wizard-state (> tab-number 1)) [:div.separator])
      (when (and wizard-state (= wizard-state :visited)) (components/icon :check-circle-wizard "icon-small wizard-check"))
      (when (and wizard-state (not= wizard-state :visited)) [:div.wizard-number tab-number])
      title]]))

(defn ul-menu [items selected]
  [:ul (map-indexed (fn [idx item]
                      [li-menu-item (assoc item
                                           :key idx
                                           :selected selected)])
                    items)])

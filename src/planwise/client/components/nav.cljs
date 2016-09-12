(ns planwise.client.components.nav
  (:require [planwise.client.components.common :as components]
            [clojure.string :as string]))

;; Navigation components

(defn- li-menu-item [{:keys [selected li-classes item href title reference]}]
  (let [is-selected? (or (= selected item)
                         (item selected))
        li-all-classes (if is-selected?
                      (conj li-classes "active")
                      li-classes)]
    [:li {:class (string/join " " li-all-classes)}
     [:a {:href href} reference title]]))

(defn- li-wizard-menu-item [{:keys [wizard-state tab-number] :as item}]
  (let [li-classes #{(name wizard-state)}
        reference (if (= wizard-state :visited)
                    (components/icon :check-circle-wizard "icon-small wizard-check")
                    [:div.wizard-number tab-number])]
    ;; (when (> tab-number 1) [:div.separator]) TODO: solve it with :after and not-first-child
    (li-menu-item (assoc item
                        :li-classes li-classes
                        :reference  reference))))

(defn- li-normal-menu-item [item]
  (li-menu-item (assoc item
                       :li-classes #{}
                       :reference (some-> (:icon item) (components/icon "icon-small")))))

(defn ul-menu [items selected wizard-mode-on]
  [:ul {:class (when wizard-mode-on "wizard")} (map-indexed (fn [idx item]
                      (let [item-with-metadata (assoc item :key idx :selected selected)]
                        (if wizard-mode-on
                          [li-wizard-menu-item item-with-metadata]
                          [li-normal-menu-item item-with-metadata])))
                        items)])

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
  (let [li-classes [(name wizard-state)]
        reference (if (= wizard-state :visited)
                    (components/icon :check-circle-wizard "icon-small wizard-check")
                    [:span.wizard-number tab-number])]
    (li-menu-item (assoc item
                         :li-classes li-classes
                         :reference  reference))))

(defn- li-normal-menu-item [item]
  (li-menu-item (assoc item
                       :reference (some-> (:icon item) (components/icon "icon-small")))))

(defn ul-menu [items selected wizard-mode-on]
  (let [item-fn (if wizard-mode-on li-wizard-menu-item li-normal-menu-item)]
    [:ul {:class (when wizard-mode-on "wizard")}
     (map-indexed (fn [idx item]
                    [item-fn (assoc item :key idx :selected selected)])
                  items)]))

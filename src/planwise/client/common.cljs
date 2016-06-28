(ns planwise.client.common
  (:require [reagent.core :as reagent]))

;; Navigation components

(defn li-menu-item [{:keys [selected item href title]}]
  (let [is-selected? (or (= selected item)
                         (item selected))]
    [:li {:class (when is-selected? "active")}
     [:a {:href href} title]]))

(defn ul-menu [items selected]
  [:ul (map-indexed (fn [idx item]
                      [li-menu-item (assoc item
                                           :key idx
                                           :selected selected)])
                    items)])



;; Modal dialog

(defn modal-dialog [{:keys [on-backdrop-click] :as props} & children]
  (let [children (if (map? props) children [props])]
    [:div.modal
     [:div.backdrop]
     (into [:div.modal-container {:on-click
                                  (fn [e] (when (and (= (aget e "target")
                                                        (aget e "currentTarget"))
                                                     on-backdrop-click)
                                            (on-backdrop-click)))}]
           children)]))



;; Filter checkboxes

(defn labeled-checkbox [{:keys [value label checked toggle-fn]}]
  (let [elt-id (str "checkbox-" (hash value))
        options (some->> toggle-fn (assoc nil :on-change))]
    [:div
     [:input (assoc options
                    :type "checkbox"
                    :id elt-id
                    :value value
                    :checked checked)]
     [:label {:for elt-id} label]]))

(defn filter-checkboxes [{:keys [options value toggle-fn]}]
  (let [value (set value)]
    (map-indexed (fn [idx option]
                   (let [option-value (:value option)
                         checked (value option-value)
                         callback-fn (when toggle-fn #(toggle-fn option-value))]
                     [labeled-checkbox (assoc option
                                              :key idx
                                              :checked checked
                                              :toggle-fn callback-fn)]))
                 options)))

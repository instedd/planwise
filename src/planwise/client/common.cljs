(ns planwise.client.common
  (:require [reagent.core :as reagent]))

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

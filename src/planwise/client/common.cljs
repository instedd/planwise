(ns planwise.client.common
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async :refer [chan >! <! put!]]))

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

(defn loading-placeholder []
  [:div.loading
   [:h3 "Loading..."]])


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

(defn close-button [props]
  [:button.mini.close (assoc props :type "button") "\u2716"])

(defn refresh-button [props]
  [:button.mini.refresh (assoc props :type "button") "\u21bb"])

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

;; Progress bar

(defn progress-bar
  ([value]
   (let [percent (* 100 (min 1 (max 0 value)))]
     [:div.progress-bar
      [:div.progress-filled {:style {:width (str percent "%")}}]]))
  ([numerator denominator]
   (let [quotient (if (zero? denominator) 0 (/ numerator denominator))]
     (progress-bar quotient))))


;; Debounce functions

(defn debounced [f timeout]
  (let [id (atom nil)]
    (fn [& args]
      (js/clearTimeout @id)
      (condp = (first args)
        :cancel nil
        :immediate (apply f (drop 1 args))
        (reset! id (js/setTimeout
                    (apply partial (cons f args))
                    timeout))))))

;; Event handlers

(defn prevent-default [f]
  (fn [evt]
    (.preventDefault evt)
    (f)))

(defn with-confirm [f confirm-msg]
  (prevent-default
    #(when (.confirm js/window confirm-msg)
       (f))))

;; Handler for API asyncs

(defn async-handle [c success-fn]
  (go
   (let [result (<! c)]
     (condp = (:status result)
       :ok (success-fn (:data result))
       :error (.error js/console (str "Error " (:code result) " performing AJAX request: " (:message result)))))))

;; Utility functions

(defn pluralize
  ([count singular]
   (pluralize count singular (str singular "s")))
  ([count singular plural]
   (let [noun (if (= 1 count) singular plural)]
     (str count " " noun))))

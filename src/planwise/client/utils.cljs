(ns planwise.client.utils
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan >! <! put!]]))

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

;; Utility functions

(defn pluralize
  ([count singular]
   (pluralize count singular (str singular "s")))
  ([count singular plural]
   (let [noun (if (= 1 count) singular plural)]
     (str count " " noun))))

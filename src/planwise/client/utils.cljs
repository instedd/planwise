(ns planwise.client.utils
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan >! <! put!]]
            [goog.i18n.NumberFormat]
            [goog.string :as gstring]
            [goog.string.format]))

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

(defn format-number
  ([number]
   (when number
     (let [format-string (if (integer? number) "#,###" "#,###.00")
           formatter (new goog.i18n.NumberFormat format-string)]
       (.format formatter number)))))

(defn format-percentage
  ([x]
   (format-percentage x 0))
  ([x decimals]
   (let [x (min 1 (max 0 x))
         percentage (* 100 x)
         format-string (str "%." decimals "f%%")]
     (gstring/format format-string percentage))))

(defn remove-by-id
  [coll id]
  (remove #(= id (:id %)) coll))

(defn remove-by
  [coll field value]
  (remove #(= value (field %)) coll))

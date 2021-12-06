(ns planwise.client.utils
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-frame.utils :as c]
            [clojure.string :as cstring]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.i18n.NumberFormat]
            [planwise.common :as common]))

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

(defn reset-fn [atom]
  (fn [evt]
    (reset! atom (-> evt .-target .-value str))))

(defn dispatch-value-fn [event]
  (fn [js-evt]
    (dispatch [event (-> js-evt .-target .-value str)])))


;; String functions

(defn or-blank
  [value fallback]
  (cond
    (or (nil? value) (= value "")) fallback
    :else value))

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

(defn format-currency
  [value]
  (str common/currency-symbol " " (format-number value)))

(defn format-effort
  [effort analysis-type]
  ((if (common/is-budget analysis-type) format-currency format-number) effort))

; Copied from https://github.com/teropa/hiccups/blob/master/src/cljs/hiccups/runtime.cljs#L30-L34
(defn escape-html
  [text]
  (cstring/escape text {\& "&amp;", \< "&lt;", \> "&gt;", \" "&quot;"}))

;; Collection utils

(defn find-by-id
  [coll id]
  (reduce #(when (= id (:id %2)) (reduced %2)) nil coll))

(defn remove-by-id
  [coll id]
  (remove #(= id (:id %)) coll))

(defn remove-by-index
  [coll index]
  (vec (keep-indexed #(if (not= %1 index) %2) coll)))

(defn update-by-id
  [coll id update-fn & args]
  (map (fn [item]
         (if (= id (:id item))
           (apply update-fn item args)
           item))
       coll))

(defn replace-by-id
  [coll replacement]
  (update-by-id coll (:id replacement) (constantly replacement)))

(defn remove-by
  [coll field value]
  (remove #(= value (field %)) coll))

(defn index-by
  [f coll]
  (into {} (map (juxt f identity) coll)))

;; Validation

(defn is-valid-email?
  [email]
  (re-matches #"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}" email))


;; Filter options by value and return label for value
;; Used when creating a disabled-input's
(defn label-from-options
  [options value empty-label]
  (let [filtered        (filter #(= (:value %) value) options)
        filtered-label  (:label (first filtered))]
    (if (empty? filtered)
      empty-label
      filtered-label)))

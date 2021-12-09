(ns planwise.util.str
  (:require [clojure.string :as str]))

(defn trim-to-int [s]
  (let [trimmed (if (string? s) (-> s str/trim-newline str/trim) s)]
    (Integer. trimmed)))

(defn try-trim-to-int [s]
  (try
    (trim-to-int s)
    (catch Exception e nil)))


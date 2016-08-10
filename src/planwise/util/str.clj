(ns planwise.util.str
  (:require [clojure.string :as str]))

(defn trim-to-int [s]
  (let [trimmed (if (string? s) (-> s str/trim-newline str/trim) s)]
    (Integer. trimmed)))

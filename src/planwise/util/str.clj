(ns planwise.util.str
  (:require [clojure.string :as str]))

(defn trim-to-int [s]
  (let [trimmed (if (string? s) (-> s str/trim-newline str/trim) s)]
    (Integer. trimmed)))

(defn try-trim-to-int [s]
  (try
    (trim-to-int s)
    (catch Exception e nil)))

(defn next-char [letter]
  (-> letter int inc char))

(defn next-alpha-name
  [name]
  (loop [prefix name
         sufix   ""]
    (let [prefix* (apply str (drop-last prefix))
          last    (last prefix)]
      (cond (empty? prefix) (str \A sufix)
            (not= last \Z) (str prefix* (next-char last) sufix)
            :else (recur prefix* (str \A sufix))))))

(defn extract-name-and-copy-number
  [s]
  (let [match      (re-matches #"\A(?<name>.*)(?<copy>\d+?)\Z" s)
        name-group (get match 1)
        copy-group (last match)]
    (cond
      (nil? match)      {:name s :copy 0}
      :else             {:name name-group :copy (Integer. copy-group)})))

(defn next-name
  [col]
  (let [names-and-numbers (map extract-name-and-copy-number col)
        first-name (:name (first names-and-numbers))
        next-copy  (->> names-and-numbers
                        (filter #(= (:name %) first-name))
                        (map :copy)
                        (apply max)
                        (inc))]
    (str first-name next-copy)))

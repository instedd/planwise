(ns planwise.util.str
  (:require [clojure.string :as str]))

(defn trim-to-int [s]
  (let [trimmed (if (string? s) (-> s str/trim-newline str/trim) s)]
    (Integer. trimmed)))

(defn try-trim-to-int [s]
  (try
    (trim-to-int s)
    (catch Exception e nil)))

(defn alphabet
  [position]
  (let [alphabet (map char (range 97 123))]
    (str/upper-case (nth alphabet position))))

(defn generate-alpha-name
  [copy]
  (loop [res ""
         num copy]
    (if (< num 26)
      (str (alphabet num) res)
      (recur (str res (alphabet (mod num 26))) (dec (int (/ num 26.0)))))))

(defn extract-name-and-copy-number
  [s]
  (let [match      (re-matches #"\A(?<name>.*)( copy( (?<copy>\d+))?)\Z" s)
        name-group (get match 1)
        copy-group (last match)]
    (cond
      (nil? match)      {:name s :copy 0}
      (nil? copy-group) {:name name-group :copy 1}
      :else             {:name name-group :copy (Integer. copy-group)}))) ()

(defn next-name
  [col]
  (let [names-and-numbers (map extract-name-and-copy-number col)
        first-name (:name (first names-and-numbers))
        next-copy  (->> names-and-numbers
                        (filter #(= (:name %) first-name))
                        (map :copy)
                        (apply max)
                        (inc))]
    (cond
      (= next-copy 1) (str first-name " copy")
      :else (str first-name " copy " next-copy))))

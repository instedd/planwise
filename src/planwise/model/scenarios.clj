(ns planwise.model.scenarios
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))


;;; Specs

(s/def ::investment int?)
(s/def ::capacity int?)
(s/def :planwise.scenarios.new-change/id string?)
(s/def :planwise.scenarios.change/id number?)
(s/def ::location map?)
(s/def ::name string?)

(s/def ::base-change
  (s/keys :req-un [::investment ::capacity]))

(s/def ::create-provider
  (s/keys :req-un [:planwise.scenarios.new-change/id ::location ::name]))

(s/def ::upgrade-provider
  (s/keys :req-un [:planwise.scenarios.change/id]))

(s/def ::increase-provider
  (s/keys :req-un [:planwise.scenarios.change/id]))


(defmulti change :action)
(defmethod change "create-provider" [_]
  (s/merge ::base-change ::create-provider))
(defmethod change "upgrade-provider" [_]
  (s/merge ::base-change ::upgrade-provider))
(defmethod change "increase-provider" [_]
  (s/merge ::base-change ::increase-provider))

(s/def ::change (s/multi-spec change :action))

(s/def ::change-set
  (s/coll-of ::change))


;;; Scenario predicates

(defn is-initial-scenario?
  [scenario]
  (= "initial" (:label scenario)))


;;; Naming scenario copies

(defn- default-scenario-suffixes
  "Extract scenario name suffixes for default names"
  [existing-names]
  (->> existing-names
       (map #(when-let [[_ suffix] (re-find #"^Scenario ([A-Z].*|[0-9]+)" %)] suffix))
       (filter some?)))

(defn- next-alpha-suffix
  "Given a list of suffixes, find the next capital letter after the last
  present. If 'Z...' already exists, returns nil."
  [suffixes]
  (if-let [alpha-suffix (->> suffixes
                             (filter #(re-find #"^[A-Z]" %))
                             sort
                             last)]
    (let [first-letter (first alpha-suffix)
          next-letter  (char (min (int \Z) (-> first-letter int inc)))]
      (when-not (= next-letter first-letter)
        next-letter))
    "A"))

(defn- next-numeric-suffix
  "Given a list of suffixes, computes the next integer after the biggest number present."
  [suffixes]
  (if-let [numeric-suffix (->> suffixes
                               (filter #(re-matches #"^[0-9]+" %))
                               (map #(Integer/parseInt %))
                               sort
                               last)]
    (str (inc numeric-suffix))
    "1"))

(defn next-name-from-initial
  [existing-names]
  (let [suffixes    (default-scenario-suffixes existing-names)
        next-suffix (or (next-alpha-suffix suffixes)
                        (next-numeric-suffix suffixes))]
    (str "Scenario " next-suffix)))

(defn- prefix-and-copy-number
  [name]
  (if-let [[_ prefix copy-number] (re-matches #"^(.*)\s*\(([0-9]+)\)$" name)]
    [(str/trimr prefix) (some-> copy-number Integer/parseInt)]
    [(str/trimr name) 0]))

(defn- find-latest-copy
  [existing-names prefix]
  (or (->> existing-names
           (map prefix-and-copy-number)
           (filter #(= prefix (first %)))
           (map second)
           sort
           max
           last)
      0))

(defn next-name-for-copy
  [existing-names original]
  (let [[prefix copy-number] (prefix-and-copy-number original)
        latest-copy          (find-latest-copy existing-names prefix)]
    (str prefix " (" (inc (max copy-number latest-copy)) ")")))

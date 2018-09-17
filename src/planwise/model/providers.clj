(ns planwise.util.providers
  (:require [planwise.util.collections :refer [sum-by merge-collections-by]]))

(defn merge-provider
  "Merge two or more provider-like maps, but sum their capacity related fields
  and the satisfied demand."
  [& providers]
  (-> (apply merge providers)
      (assoc :capacity (sum-by :capacity providers)
             :satisfied-demand (sum-by :satisfied-demand providers)
             :used-capacity (sum-by :used-capacity providers)
             :free-capacity (sum-by :free-capacity providers))))

(defn merge-providers
  "Merge providers by id, but perform addition for the fields :capacity,
  :satisfied-demand, :used-capacity and :free-capacity."
  [& colls]
  (apply merge-collections-by :id merge-provider colls))

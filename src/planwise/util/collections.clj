(ns planwise.util.collections)

(defn find-by
  [coll field value]
  (reduce #(when (= value (field %2)) (reduced %2)) nil coll))

(defn sum-by
  [key coll]
  (reduce + (filter number? (map key coll))))

(defn merge-collections-by
  [key merge-fn & colls]
  (map (fn [[id same-key-maps]] (apply merge-fn same-key-maps))
       (group-by key (apply concat colls))))

(defn update-merged-coll-in-fields
  [fields coll]
  (let [update-fields #(reduce
                        (fn [p field] (assoc p field (sum-by field coll)))
                        %
                        fields)]
    (update-fields (apply merge coll))))

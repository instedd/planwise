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

(defn map-vals
  [f m]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} m))

(defn csv-data->maps
  "Converts the result of csv/read-csv (ie. a collection of vectors of strings)
  into a collection of maps using the first row (converted to keywords) as keys"
  [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
       (rest csv-data)))

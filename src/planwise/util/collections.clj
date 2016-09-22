(ns planwise.util.collections)

(defn find-by
  [coll field value]
  (reduce #(when (= value (field %2)) (reduced %2)) nil coll))

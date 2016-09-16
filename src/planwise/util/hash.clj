(ns planwise.util.hash)

(defn update-if-contains
  [hash key fun & args]
  (if (contains? hash key)
    (apply update hash key fun args)
    hash))

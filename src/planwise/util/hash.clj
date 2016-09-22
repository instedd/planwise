(ns planwise.util.hash)

(defn update-if-contains
  [hash key fun & args]
  (if (contains? hash key)
    (apply update hash key fun args)
    hash))

(defn update-if
  [hash key fun & args]
  (if-not (nil? (get hash key))
    (apply update hash key fun args)
    hash))

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

(defn update*
  [hash key fun & args]
  (if-not (nil? hash)
    (apply update hash key fun args)
    hash))

(defn dissoc*
  [hash path]
  (let [key  (last path)
        path (butlast path)
        only-key? (zero? (dec (count (get-in hash path))))]
    (if only-key? (assoc-in hash path nil) (update-in hash path dissoc key))))
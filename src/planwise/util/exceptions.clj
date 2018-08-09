(ns planwise.util.exceptions)

(defn catch-exc
  [function & params]
  (try
    (apply function params)
    (catch Exception e
      nil)))

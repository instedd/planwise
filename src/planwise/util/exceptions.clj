(ns planwise.util.exceptions)

(defn catch-exception
  [response-fn function & params]
  (try
    (apply function params)
    (catch Exception e
      (when response-fn (response-fn e)))))

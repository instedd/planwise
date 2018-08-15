(ns planwise.util.exceptions)

(defn catch-exc
  [response-fn function & params]
  (try
    (apply function params)
    (catch Exception e
      (when response-fn (response-fn e)))))

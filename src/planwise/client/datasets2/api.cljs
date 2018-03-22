(ns planwise.client.datasets2.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-datasets2
  {:method    :get
   :uri       "/api/datasets2"})

(defn create-dataset-with-csv
  [{:keys [name csv-file coverage-algorithm]}]
  (let [form-data (doto (js/FormData.)
                    (.append "name" name)
                    (.append "file" csv-file)
                    (.append "coverage-algorithm" (cljs.core/name coverage-algorithm)))]
    {:method    :post
     :uri       "/api/datasets2"
     :body      form-data}))

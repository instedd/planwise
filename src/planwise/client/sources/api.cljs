(ns planwise.client.sources.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-sources
  {:method    :get
   :uri       "/api/sources"})

(defn create-source-with-csv
  [{:keys [name unit csv-file]}]
  (let [form-data (doto (js/FormData.)
                    (.append "name" name)
                    (.append "unit" unit)
                    (.append "csvfile" csv-file))]
    {:method  :post
     :uri     "/api/sources"
     :body    form-data}))

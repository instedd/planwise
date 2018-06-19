(ns planwise.client.sources.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-sources
  {:method    :get
   ;:section   :show
   :uri       "/api/sources"})

(defn create-source-with-csv
  [{:keys [name csv-file]}]
  (let [form-data (doto (js/FormData.)
                    (.append "name" name)
                    (.append "csvfile" csv-file))]
    {:method  :post
     :uri     "/api/sources"
     :body    form-data}))

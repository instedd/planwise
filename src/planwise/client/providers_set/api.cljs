(ns planwise.client.providers-set.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-providers-set
  {:method    :get
   :uri       "/api/providers"})

(defn create-provider-set-with-csv
  [{:keys [name csv-file coverage-algorithm]}]
  (let [form-data (doto (js/FormData.)
                    (.append "name" name)
                    (.append "file" csv-file)
                    (.append "coverage-algorithm" (cljs.core/name coverage-algorithm)))]
    {:method    :post
     :uri       "/api/providers"
     :body      form-data}))

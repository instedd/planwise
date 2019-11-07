(ns planwise.client.providers-set.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-providers-set
  {:method    :get
   :uri       "/api/providers"})

(defn create-provider-set-with-csv
  [{:keys [name csv-file]}]
  (let [form-data (doto (js/FormData.)
                    (.append "name" name)
                    (.append "file" csv-file))]

    {:method    :post
     :uri       "/api/providers"
     :body      form-data}))

(defn delete-provider-set
  [id]
  {:method :delete
   :params {:id id}
   :uri     "/api/providers"})

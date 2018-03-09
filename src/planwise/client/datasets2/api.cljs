(ns planwise.client.datasets2.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-datasets2
  {:method    :get
   :uri       "/api/datasets2"})

(defn creating-dataset-with-uploaded-sites!
  [form-data]
  {:method    :post
   :uri       "/api/datasets2"
   :body      form-data})

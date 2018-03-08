(ns planwise.client.datasets2.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-datasets2
  {:method    :get
   :uri       "/api/datasets2"})

(defn create-dataset2!
  [name]
  {:method    :post
   :uri       "/api/datasets2/only-dataset"
   :params    {:name    name}})

(defn load-csv-file!
  [form-data]
  {:method    :post
   :uri       "/api/datasets2/sites"
   :body    form-data})

(defn creating-dataset-with-uploaded-sites!
  [name form-data]
  {:method    :post
   :uri       "/api/datasets2/sites"
   :params    {:name    name}
   :body    form-data})

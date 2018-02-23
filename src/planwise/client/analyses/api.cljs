(ns planwise.client.analyses.api)

;; ----------------------------------------------------------------------------
;; API methods

(def load-analyses
  {:method :get
   :uri    "/api/analyses"})

(defn create-analysis!
  [name]
  {:method :post
   :uri    "/api/analyses"
   :params {:name name}})

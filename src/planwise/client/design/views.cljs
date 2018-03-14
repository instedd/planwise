(ns planwise.client.design.views
  (:require [planwise.client.ui.rmwc :as rmwc]))

(defn app
  []
  [:article.design
    [rmwc/TextField {:label "Lorem"}]
    [rmwc/Button {} "I'm a button"]
    [rmwc/Fab {} "favorite"]])

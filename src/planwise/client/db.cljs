(ns planwise.client.db)

(def initial-position-and-zoom {:position [-1.29 36.83]
                                :zoom 9})

(def initial-db
  {:current-page :home
   :playground {:map-view initial-position-and-zoom
                :loading? false
                :threshold 600
                :node-id nil
                :points []}})

(defn playground-threshold [db]
  (get-in db [:playground :threshold]))

(defn playground-node-id [db]
  (get-in db [:playground :node-id]))

(defn playground-points [db]
  (get-in db [:playground :points]))

(ns planwise.client.playground.db)

(defn isochrone-params [db]
  (select-keys db [:threshold :simplify :algorithm]))

(def initial-position-and-zoom {:position [-0.0236 37.9062]
                                :zoom 7})

(def initial-db
  {:map-view initial-position-and-zoom
   :loading? false
   :threshold 3600
   :algorithm "alpha-shape"
   :simplify 0.0
   :node-id nil
   :points []})

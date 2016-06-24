(ns planwise.client.playground.db)

(defn isochrone-params [db]
  (select-keys db [:threshold :simplify :algorithm]))

(ns planwise.client.asdf
  [:refer-clojure :exclude [reset! swap!]]
  [:require [schema.core :as s]])

(s/defschema AsdfState (s/enum :invalid :reloading :valid))

(s/defrecord AsdfBase
    [state :- AsdfState
     value :- s/Any])

(defn Asdf
  [value-schema]
  {:state AsdfState
   :value value-schema})

(defn new
  [value]
  (map->AsdfBase {:state :invalid :value value}))

(defn value
  [asdf]
  (:value asdf))

(defn state
  [asdf]
  (:state asdf))

(defn valid?
  [asdf]
  (= :valid (:state asdf)))

(defn reloading?
  [asdf]
  (= :reloading (:state asdf)))

(defn reset!
  [asdf value]
  (assoc asdf
         :state :valid
         :value value))

(defn swap!
  [asdf swap-fn & args]
  (assoc asdf
         :state :valid
         :value (apply swap-fn (:value asdf) args)))

(defn invalidate!
  ([asdf]
   (assoc asdf :state :invalid))
  ([asdf update-fn & args]
   (assoc asdf
          :state :invalid
          :value (apply update-fn (:value asdf) args))))

(defn reload!
  [asdf]
  (assoc asdf :state :reloading))

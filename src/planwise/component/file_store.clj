(ns planwise.component.file-store
  (:require [planwise.boundary.file-store :as boundary]
            [planwise.util.files :as files]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)


;; Service implementation ====================================================
;;

(defn- build-collection-path
  [{:keys [data-path]} coll-type coll-id]
  (when (nil? data-path)
    (throw (ex-info "missing data-path configuration attribute in file store service")))
  (boundary/full-path data-path (name coll-type) (boundary/build-file-id coll-id)))

(defn- setup-collection
  [service coll-type coll-id]
  (s/assert ::boundary/coll-type coll-type)
  (s/assert ::boundary/coll-id coll-id)
  (let [coll-path (build-collection-path service coll-type coll-id)]
    (debug (str "Setting up file store collection " [coll-type coll-id] " at " coll-path))
    (io/make-parents (boundary/full-path coll-path "dummy"))
    coll-path))

(defn- destroy-collection
  [service coll-type coll-id]
  (s/assert ::boundary/coll-type coll-type)
  (s/assert ::boundary/coll-id coll-id)
  (let [coll-path (build-collection-path service coll-type coll-id)]
    (debug (str "Destroying file store collection " [coll-type coll-id] " at " coll-path))
    (files/delete-files-recursively coll-path :silent)))


;; Service definition ========================================================
;;

(defrecord FileStore [data-path])

(defmethod ig/init-key :planwise.component/file-store
  [_ config]
  (map->FileStore config))

(extend-protocol boundary/FileStore
  FileStore

  (setup-collection [this coll-type coll-id]
    (setup-collection this coll-type coll-id))

  (destroy-collection [this coll-type coll-id]
    (destroy-collection this coll-type coll-id)))


;; REPL testing ===============================================================
;;

(comment
  (def service {:data-path "data/"})

  (setup-collection service :coverages [:project 1])
  (destroy-collection service :coverages [:project 1])

  nil)

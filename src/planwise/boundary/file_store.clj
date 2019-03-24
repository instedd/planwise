(ns planwise.boundary.file-store
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.java.io :as io]))


;; Specs =====================================================================
;;

(s/def ::coll-type #{:coverages :projects})
(s/def ::coll-id some?)


;; Protocol defintions =======================================================
;;

(defprotocol FileStore
  (setup-collection [this coll-type coll-id]
    "Constructs a file path to the collection and ensures the directory for it
    exists. Returns the full path to the collection directory.")

  (destroy-collection [this coll-type coll-id]
    "Removes the directory and contained files in the given collection"))


;; Auxiliary functions =======================================================
;;

(defn build-file-id
  "Construct a valid filename derived from any value"
  [id]
  (let [file-id (-> (if (string? id) id (pr-str id))
                    (str/replace #"[:\[\]\(\){}]" "")
                    (str/replace #"[ \".]" "-")
                    (str/replace #"[/]" "_")
                    (str/replace #"[^0-9a-zA-Z_-]" ""))]
    (when (> (count file-id) 200)
      (throw (ex-info "Derived file id too large" {:id id :file-id file-id})))
    file-id))

(defn full-path
  "Builds a full path by joining parts with the OS separator"
  [parent & more]
  (str (apply io/file parent more)))

(defn exists?
  [path]
  (if (some? path)
    (.exists (io/as-file path))
    false))

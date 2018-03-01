(ns planwise.tasks.build-icons
  (:require [clojure.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [taoensso.timbre :as timbre])
  (:gen-class))

(timbre/refer-timbre)

(defn process-svg [file]
  (info " Processing " file)
  (with-open [input (io/input-stream file)]
    (let [xml-tree    (xml/parse input)
          title-tag   (filter #(= :title (:tag %)) (xml-seq xml-tree))
          name        (-> title-tag first :content first)
          updated-xml (-> xml-tree
                        (assoc-in [:attrs :id] (str "icon-" name))
                        (assoc-in [:attrs :data-name] (str "icon-" name))
                        (update-in [:attrs] dissoc :xmlns)
                        (assoc :tag :symbol))]
      updated-xml)))

(defn wrap-in-svg [symbols]
  {:tag     :svg,
   :attrs   {:xmlns "http://www.w3.org/2000/svg"
             :width "0"
             :height "0"
             :class "svg-hidden"}
   :content (vec symbols)})

(defn process-svgs []
  (let [target    "resources/svg/icons.svg"
        dirname   "resources/svg/icons"
        svg       (->> dirname
                    (io/file)
                    (file-seq)
                    (filter #(string/ends-with? % ".svg"))
                    (map process-svg)
                    (wrap-in-svg))]
    (spit target
          (str
            "<!-- Auto-generated via build-icons lein task -->\n"
            (with-out-str (xml/emit-element svg))))))

(defn -main [& args]
  (timbre/set-level! :info)
  (info "Creating resources/svg/icons.svg from resources/svg/icons/*")
  (process-svgs))

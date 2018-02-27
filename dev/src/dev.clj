(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [fipp.edn :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.core.repl :as duct-repl]
            [duct.repl.figwheel :refer [cljs-repl]]
            [eftest.runner :as eftest]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt go init prep reset]]
            [integrant.repl.state :refer [config system]]

            [planwise.repl :refer :all]

            #_[taoensso.timbre :as timbre]
            #_[schema.core :as s]
            #_[meta-merge.core :refer [meta-merge]]
            #_[ring.middleware.stacktrace :refer [wrap-stacktrace wrap-stacktrace-log]]
            #_[dev.figwheel :as figwheel]
            #_[dev.tasks :refer :all]
            #_[dev.sass :as sass]
            #_[dev.auto :as auto]
            #_[ring.mock.request :as mock]
            #_[planwise.config :as config]
            #_[planwise.system :as system]))

(duct/load-hierarchy)

(defn read-config []
  (duct/read-config (io/resource "dev.edn")))

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(when (io/resource "local.clj")
  (load "local"))

(integrant.repl/set-prep! (comp duct/prep read-config))


;; ;; Logging configuration for development
;; (timbre/merge-config! {:level :debug
;;                        :ns-blacklist ["com.zaxxer.hikari.*"
;;                                       "org.apache.http.*"
;;                                       "org.eclipse.jetty.*"]})

;; ;; Fix JWE secret in development to facilitate debugging
;; (def jwe-secret
;;   "12345678901234567890123456789012")

;; (def dev-config
;;   {:app {:middleware [[wrap-stacktrace :stacktrace-options]]
;;          :stacktrace-options {:color? true}}
;;    :api {:middleware [[wrap-stacktrace-log :stacktrace-options]]
;;          :stacktrace-options {:color? true}}
;;    :auth {:jwe-secret jwe-secret}
;;    :paths {:bin "cpp/"
;;            :scripts "scripts/"
;;            :data "data/"}
;;    :maps {:facilities-capacity 1000000}
;;    :mailer {:mock? true}
;;    :figwheel
;;    {:css-dirs ["resources/planwise/public/css"
;;                "target/sass-repl"]
;;     :reload-clj-files false
;;     :builds   [{:source-paths ["src" "dev"]
;;                 :build-options
;;                 {:optimizations :none
;;                  :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
;;                  :main "cljs.user"
;;                  :asset-path "/js"
;;                  :output-to  "target/figwheel/planwise/public/js/main.js"
;;                  :output-dir "target/figwheel/planwise/public/js"
;;                  :source-map true
;;                  :source-map-path "/js"}}]}
;;    :sass
;;    {:input "resources/sass/site.scss"
;;     :output "target/sass-repl/planwise/public/css/site.css"
;;     :options ["-m" "auto"]}})

;; (def config
;;   (meta-merge config/defaults
;;               config/environ
;;               dev-config))

;; (defn new-system []
;;   (into (system/new-system config)
;;         {:sass (sass/sass-compiler (:sass config))
;;          :figwheel (component/using
;;                     (figwheel/server (:figwheel config))
;;                     [:sass])
;;          :auto (component/using
;;                 (auto/auto-builder {:enabled true})
;;                 [:figwheel :sass])
;;          :ragtime (component/using
;;                    (ragtime {:resource-path "migrations"})
;;                    [:db])}))

;; (defn db []
;;   (:spec (:db system)))

;; ;; Activate Schema validation for *all* functions
;; (s/set-fn-validation! true)

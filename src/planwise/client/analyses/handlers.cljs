(ns planwise.client.analyses.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [re-frame.utils :as c]
            [clojure.string :refer [blank?]]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :refer [remove-by-id]]
            [planwise.client.analyses.api :as api]
            [planwise.client.analyses.db :as db]
            [planwise.client.utils :as utils]))

(def in-analyses (path [:analyses]))

;; ----------------------------------------------------------------------------
;; Analyses listing

(register-handler
 :analyses/load-analyses
 in-analyses
 (fn [db [_]]
   (api/load-analyses :analyses/analyses-loaded)
   (update db :list asdf/reload!)))

(register-handler
 :analyses/invalidate-analyses
 in-analyses
 (fn [db [_]]
   (update db :list asdf/invalidate!)))

(register-handler
 :analyses/analyses-loaded
 in-analyses
 (fn [db [_ analyses]]
   (update db :list asdf/reset! analyses)))

(register-handler
 :analyses/create-analysis!
 in-analyses
 (fn [db [_]]
   (api/create-analysis! "Test" :analyses/invalidate-analyses)
   db))

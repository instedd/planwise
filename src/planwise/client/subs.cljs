(ns planwise.client.subs
  (:require [re-frame.core :as rf]
            [planwise.client.projects.subs]
            [planwise.client.current-project.subs]
            [planwise.client.datasets.subs]
            [planwise.client.analyses.subs]
            [planwise.client.regions.subs]))


;; Subscriptions
;; -------------------------------------------------------

(rf/reg-sub
 :current-page
 (fn [db _]
   (:current-page db)))

(rf/reg-sub
 :page-params
 (fn [db _]
   (:page-params db)))

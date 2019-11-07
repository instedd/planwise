(ns planwise.client.projects2.subs
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :projects2/current-project
 (fn [db _]
   (get-in db [:projects2 :current-project])))

(rf/reg-sub
 :projects2/templates
 (fn [db _]
   (get-in db [:projects2 :templates])))

(rf/reg-sub
 :projects2/list
 (fn [db _]
   (some->> (get-in db [:projects2 :list])
            (sort-by (comp string/lower-case :name)))))

(rf/reg-sub
 :projects2/tags :<- [:projects2/current-project]
 (fn [current-project [_]]
   (get-in current-project [:config :providers :tags])))

(rf/reg-sub
 :projects2/build-actions :<- [:projects2/current-project]
 (fn [current-project [_]]
   (get-in current-project [:config :actions :build])))

(rf/reg-sub
 :projects2/upgrade-actions :<- [:projects2/current-project]
 (fn [current-project [_]]
   (get-in current-project [:config :actions :upgrade])))

(rf/reg-sub
 :projects2/new-project-coverage :<- [:projects2/current-project]
 (fn [current-project [_]]
   (get-in current-project [:coverage-algorithm])))

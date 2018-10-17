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
 :projects2/providers-layer
 (fn [db []]
   (get-in db [:projects2 :providers-layer])))

(defn- get-filter-options
  [project]
  (-> (select-keys project [:projects2 :region-id :coverage-algorithm])
      (assoc :tags (get-in project [:config :providers :tags])
             :coverage-options (get-in project [:config :coverage :filter-options]))))

(rf/reg-sub
 :projects2/should-get-providers?
 (fn [db []]
   (let [last-config (get-in db [:projects2 :config-for-requested-providers])
         project     (get-in db [:projects2 :current-project])
         actual-config (get-filter-options project)]
     (case last-config
       nil (and (:provider-set-id project) (:provider-set-version project))
       actual-config false
       false))))

(rf/reg-sub
 :projects2/map-settings-class-name
 (fn [db _]
   (str (get-in db [:projects2 :current-project :display-settings]))))

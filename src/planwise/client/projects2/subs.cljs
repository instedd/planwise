(ns planwise.client.projects2.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :projects2/current-project
 (fn [db _]
   (get-in db [:projects2 :current-project])))

(rf/reg-sub
 :projects2/list
 (fn [db _]
   (get-in db [:projects2 :list])))


(ns planwise.client.datasets2.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :datasets2/list
 (fn [db _]
   (get-in db [:datasets2 :list])))

(rf/reg-sub
 :datasets2/view-state
 (fn [db _]
   (get-in db [:datasets2 :view-state])))

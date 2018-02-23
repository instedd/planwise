(ns planwise.client.analyses.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :analyses/list
 (fn [db _]
   (get-in db [:analyses :list])))

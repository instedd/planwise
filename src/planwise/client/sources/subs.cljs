(ns planwise.client.sources.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
  :sources/list
  (fn [db _]
    (get-in db [:sources :list])))

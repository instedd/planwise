(ns planwise.client.effects
  (:require [re-frame.core :as rf]
            [accountant.core :as accountant]))

(rf/reg-fx
 :navigate
 (fn [route]
   (accountant/navigate! route)))

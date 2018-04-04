(ns planwise.client.datasets2.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :datasets2/list
 (fn [db _]
   (get-in db [:datasets2 :list])))

(rf/reg-sub
 :datasets2/dropdown-options
 (fn [db _]
   (let [list (get-in db [:datasets2 :list :value])]
     (mapv (fn [dataset] (let [{:keys [id name]} dataset] {:value (str id) :label name})) list))))

(rf/reg-sub
 :datasets2/view-state
 (fn [db _]
   (get-in db [:datasets2 :view-state])))

(rf/reg-sub
 :datasets2/last-error
 (fn [db _]
   (get-in db [:datasets2 :last-error])))

(rf/reg-sub
 :datasets2/new-dataset-state
 (fn [db _]
   (get-in db [:datasets2 :new-dataset :state])))

(rf/reg-sub
 :datasets2/new-dataset-name
 (fn [db _]
   (get-in db [:datasets2 :new-dataset :name])))

(rf/reg-sub
 :datasets2/new-dataset-js-file
 (fn [db _]
   (get-in db [:datasets2 :new-dataset :js-file])))

(rf/reg-sub
 :datasets2/new-dataset-coverage
 (fn [db _]
   (get-in db [:datasets2 :new-dataset :coverage])))

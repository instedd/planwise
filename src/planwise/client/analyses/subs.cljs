(ns planwise.client.analyses.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [clojure.string :as string]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(register-sub
 :analyses/list
 (fn [db [_]]
   (reaction (get-in @db [:analyses :list]))))

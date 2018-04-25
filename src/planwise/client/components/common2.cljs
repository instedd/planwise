(ns planwise.client.components.common2
  (:require [planwise.client.config :as config]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [re-frame.core :refer [dispatch subscribe]]))

(def current-user-email
  (atom config/user-email))

(defn nav-params
  []
  (let [page (subscribe [:current-page])
        active? (map #(contains? % @page) [#{:projects2 :scenario} #{:datasets2}])]
    {:sections [[ui/section {:href (routes/projects2) :active (first active?)}  "Projects"]
                [ui/section {:href (routes/datasets2) :active (second active?)} "Datasets"]
                [ui/section {:href "/old" :target "_blank"} "Old version"]]

     :account [ui/account {:name @current-user-email :on-signout #(dispatch [:signout])}]
     :title "Planwise"
     :footer [ui/footer config/app-version]}))

(defn loading-placeholder
  []
  [ui/fixed-width (nav-params)
   [:p "Loading..."]])

(defn redirect-to
  [route]
  (accountant/navigate! route)
  [ui/fixed-width (nav-params)
   [:p "Loading..."]])

(ns planwise.client.components.common2
  (:require [planwise.client.config :as config]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [re-frame.core :refer [dispatch]]))

(def current-user-email
  (atom config/user-email))

(defn nav-params
  []
  { :sections [[ui/section {:href (routes/projects2) :className "active"} "Projects"]
               [ui/section {:href (routes/datasets2)} "Datasets"]
               [ui/section {:href "/old" :target "_blank"} "Old version"]]

                 ;;  {:item :projects2 :href (routes/projects2) :title "Projects*"}
                 ;;  {:item :datasets :href  :title "Datasets*"}])

    :account [ui/account {:name @current-user-email :on-signout #(dispatch [:signout])}]
    :title "Planwise"
    :footer [ui/footer config/app-version]})

(defn loading-placeholder
  []
  [ui/fixed-width (nav-params)
    [:p "Loading..."]])

(defn redirect-to
  [route]
  (accountant/navigate! route)
  [ui/fixed-width (nav-params)
    [:p "Loading..."]])

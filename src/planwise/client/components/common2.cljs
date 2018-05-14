(ns planwise.client.components.common2
  (:require [planwise.client.config :as config]
            [reagent.core :as r]
            [clojure.string :refer [blank?]]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [re-frame.core :refer [dispatch subscribe]]))

(def current-user-email
  (atom config/user-email))

(defn nav-params
  []
  (let [page (subscribe [:current-page])
        active? (fn [pages] (contains? pages @page))]
    {:sections [[ui/section {:href (routes/projects2) :active (active? #{:projects2 :scenario})} "Projects"]
                [ui/section {:href (routes/datasets2) :active (active? #{:datasets2})} "Datasets"]]

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

(defn text-field
  [{:keys [id label value] :as props}]
  (let [focus  (r/atom false)]
    [:div.mdc-text-field.mdc-text-field--upgraded {:class (when @focus "mdc-text-field--focused")}
    [:input#id.mdc-text-field__input (merge props {:on-focus #(reset! focus true)
                                                   :on-blur #(reset! focus false)})]
    [:label.mdc-floating-label {:for id
              :class (when-not (blank? (str value)) "mdc-floating-label--float-above")}
      label]
    [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--focused")}]]))
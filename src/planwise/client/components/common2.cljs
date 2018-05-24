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
  ([props-input]
   (text-field props-input nil))
  ([{:keys [label value] :as props-input} custom-text-prop]
   (let [focus  (r/atom false)
         id     (str (random-uuid))]
     (fn [{:keys [class label value] :as props-input} custom-text-prop]
       (let [props (dissoc props-input :label)]
         [:div.mdc-text-field.mdc-text-field--upgraded {:class (when @focus (str "mdc-text-field--focused" custom-text-prop))}
          [:input.mdc-text-field__input (merge props-input  {:id id
                                                             :on-focus #(reset! focus true)
                                                             :on-blur  #(reset! focus false)})]
          [:label.mdc-floating-label {:for id
                                      :class (when (or (not (blank? (str value))) @focus) "mdc-floating-label--float-above")}
           label]
          [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]])))))


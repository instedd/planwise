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
                [ui/section {:href (routes/providers-set) :active (active? #{:providers-set})} "Providers"]
                [ui/section {:href (routes/sources) :active (active? #{:sources})} "Sources"]]

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

(defn- set-format
  [type]
  (let [not-nan #(when-not (js/isNaN %) %)
        type (or type :integer)]
    (cond (#{:integer} type) [(fn [e] (re-find #"\d+" e)) (comp not-nan js/parseInt)]
          (#{:percentage} type) [(fn [e] (re-find #"(?u)100|\d{0,2}\.\d+|\d{0,2}\.|\d{0,2}" e)) (comp not-nan js/parseFloat)]
          (#{:float} type) [(fn [e] (re-find #"\d+\.\d+|\d+\.|\d+" e)) (comp not-nan js/parseFloat)]
          :else [identity identity])))

(defn text-field
  [props-input]
  (let [focus (r/atom false)
        id    (str (random-uuid))]
    (fn [{:keys [label value focus-extra-class on-change reset-local-value] :as props-input}]
      (let [props (dissoc props-input :label :focus-extra-class :on-change :reset-local-value)]
        [:div.mdc-text-field.mdc-text-field--upgraded {:class (when @focus (str "mdc-text-field--focused" focus-extra-class))}
         [:input.mdc-text-field__input (merge props {:id id
                                                     :on-focus #(reset! focus true)
                                                     :on-blur  #(do
                                                                  (when reset-local-value (reset-local-value))
                                                                  (on-change)
                                                                  (reset! focus false))
                                                     :on-change on-change}
                                              (when @focus
                                                {:placeholder nil}))]
         [:label.mdc-floating-label {:for id
                                     :class (when (or (not (blank? (str value))) @focus) "mdc-floating-label--float-above")}
          label]
         [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]]))))

(defn numeric-text-field
  [{:keys [value sub-type] :as props-input}]
  (let [local (r/atom (str value))
        [valid-fn parse-fn] (set-format sub-type)]
    (fn [{:keys [on-change focus-extra-class] :as props-input}]
      (let [props (dissoc props-input :sub-type :field)
            necessary? (not= focus-extra-class " invalid-input")
            wrong-input (not= (valid-fn @local) @local)]
        [text-field (assoc props :focus-extra-class (if (and wrong-input necessary?) " invalid-input" "")
                           :type "text"
                           :on-change #(do
                                         (reset! local (-> % .-target .-value str))
                                         (on-change (parse-fn (valid-fn @local))))
                           :reset-local-value #(reset! local (str (valid-fn @local)))
                           :value @local)]))))

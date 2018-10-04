(ns planwise.client.components.common2
  (:require [planwise.client.config :as config]
            [reagent.core :as r]
            [clojure.string :refer [blank?]]
            [planwise.client.ui.common :as ui]
            [planwise.client.routes :as routes]
            [accountant.core :as accountant]
            [planwise.client.utils :as utils]
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

(defn text-field
  [props-input]
  (let [focus (r/atom false)
        id    (str (random-uuid))]
    (fn [{:keys [label value focus-extra-class on-change] :as props-input}]
      (let [props (dissoc props-input :label :focus-extra-class :on-change)]
        [:div.mdc-text-field.mdc-text-field--upgraded {:class (cond (:read-only props) focus-extra-class
                                                                    @focus (str "mdc-text-field--focused" focus-extra-class)
                                                                    :else nil)}
         [:input.mdc-text-field__input (merge props {:id id
                                                     :on-focus #(reset! focus true)
                                                     :on-blur  #(reset! focus false)
                                                     :on-change on-change}
                                              (when @focus
                                                {:placeholder nil}))]
         [:label.mdc-floating-label {:for id
                                     :class (when (or (not (blank? (str value))) @focus) "mdc-floating-label--float-above")}
          label]
         [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]]))))

(defn- set-format
  [type]
  (let [not-nan #(when-not (js/isNaN %) %)
        type (or type :integer)]
    (case type
      :integer [(fn [e] (re-find #"\d+" e)) (comp not-nan js/parseInt)]
      :percentage [(fn [e] (re-find #"(?u)100|\d{0,2}\.\d+|\d{0,2}\.|\d{0,2}" e)) (comp not-nan js/parseFloat)]
      :float [(fn [e] (re-find #"\d+\.\d+|\d+\.|\d+" e)) (comp not-nan js/parseFloat)]
      [identity identity])))

(defn numeric-field
  [props-input]
  (let [focus       (r/atom false)
        local-value (r/atom (str (:value props-input)))
        id          (str (random-uuid))
        [valid-fn parse-fn] (set-format (:sub-type props-input))]
    (fn [{:keys [label not-valid? value on-change] :as props-input}]
      (let [props             (dissoc props-input :label :not-valid?)
            wrong-input       (not= (valid-fn @local-value) @local-value)
            focus-extra-class (when (or wrong-input not-valid?) " invalid-input")]
        (when-not (or @focus (= @local-value value)) (reset! local-value (str value)))
        [:div.mdc-text-field.mdc-text-field--upgraded {:class (cond (:read-only props) focus-extra-class
                                                                    @focus (str "mdc-text-field--focused" focus-extra-class)
                                                                    :else nil)}
         [:input.mdc-text-field__input (merge props {:id       id
                                                     :on-focus #(reset! focus true)
                                                     :on-blur  #(reset! focus false)
                                                     :on-change #(do
                                                                   (reset! local-value (-> % .-target .-value str))
                                                                   (on-change (parse-fn (valid-fn @local-value))))
                                                     :value (if @focus @local-value value)}
                                              (when @focus
                                                {:placeholder nil}))]
         [:label.mdc-floating-label {:for id
                                     :class (when (or (not (blank? (str value))) @focus) "mdc-floating-label--float-above")}
          label]
         [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]]))))

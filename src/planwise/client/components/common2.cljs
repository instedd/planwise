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
   [:div.spinning-loader]])

(defn redirect-to
  [route]
  (accountant/navigate! route)
  (loading-placeholder))

(defn mdc-input-field
  [props component-props]
  (let [{:keys [id focus focus-extra-class label]} component-props
        not-blank-value? (not (blank? (str (:value props))))]
    [:div.mdc-text-field.mdc-text-field--upgraded {:class (str (:class-name component-props)
                                                               (cond
                                                                 (:read-only props) focus-extra-class
                                                                 @focus (str "mdc-text-field--focused" focus-extra-class)))}
     (:extra-right-content component-props)
     [:input.mdc-text-field__input (merge props {:id id
                                                 :on-focus #(reset! focus true)
                                                 :on-blur  #(reset! focus false)}
                                          (when @focus
                                            {:placeholder nil}))]
     (:extra-left-content component-props)
     [:label.mdc-floating-label {:for id
                                 :class (str (when (or (:disable-floating-label component-props)
                                                       not-blank-value?
                                                       @focus)
                                               "mdc-floating-label--float-above")
                                             focus-extra-class)}
      label]
     [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]]))

(def extra-keys
  [:label :focus-extra-class :sub-type :invalid-input :type  :extra-right-content :disable-floating-label :class-name :extra-left-content])

(defn text-field
  [props]
  (let [focus (r/atom false)
        id    (str (random-uuid))]
    (fn [props]
      (let [component-props (assoc (select-keys props extra-keys)
                                   :id id
                                   :focus focus)
            props           (apply dissoc props extra-keys)]
        [mdc-input-field props component-props]))))

(defn- set-numeric-format
  [type]
  (let [not-nan #(when-not (js/isNaN %) %)
        type (or type :integer)]
    (case type
      :integer    [(fn [e] (re-find #"\d+" e))                                    (comp not-nan js/parseInt)]
      :percentage [(fn [e] (re-find #"(?u)100|\d{0,2}\.\d+|\d{0,2}\.|\d{0,2}" e)) (comp not-nan js/parseFloat)]
      :float      [(fn [e] (re-find #"\d+\.\d+|\d+\.|\d+" e))                     (comp not-nan js/parseFloat)]
      [identity identity])))

(defn numeric-field
  [props]
  (let [focus                  (r/atom false)
        local-value            (r/atom (str (:value props)))
        [validate-fn parse-fn] (set-numeric-format (:sub-type props))]
    (fn [props]
      (let [wrong-input     (or (not= (validate-fn @local-value) @local-value) (:invalid-input props))
            component-props (merge
                             (select-keys props extra-keys)
                             {:id    (str (random-uuid))
                              :focus focus
                              :focus-extra-class (when wrong-input " invalid-input")})
            on-change-fn    (:on-change props)
            global-value    (str (:value props))
            props           (merge
                             (apply dissoc props extra-keys)
                             {:on-change #(do
                                            (reset! local-value (-> % .-target .-value str))
                                            (on-change-fn (parse-fn (validate-fn @local-value))))
                              :value (if @focus @local-value global-value)})]
        (let [changed-global-value? (and (not @focus)
                                         (not= @local-value global-value))]
          (when changed-global-value?
            (reset! local-value (str global-value))))
        [mdc-input-field props component-props]))))

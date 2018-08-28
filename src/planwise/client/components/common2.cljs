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
  [field type]
  (let [prevent-fn (fn [f] #(let [val (f %)] (if (js/isNaN val) nil val)))
        type (or type :integer)]
    (cond (#{[:numeric :integer]} [field type]) [(fn [e] (re-find #"\d+" e)) (prevent-fn js/parseInt)]
          (#{[:numeric :percentage]} [field type]) [(fn [e] (re-find #"(?u)100|\d{0,2}\.\d+|\d{0,2}\.|\d{0,2}" e)) (prevent-fn js/parseFloat)]
          (#{[:numeric :float]} [field type]) [(fn [e] (re-find #"\d+\.\d+|\d+\.|\d+" e)) (prevent-fn js/parseFloat)]
          :else [identity identity])))

(defn text-field
  ([{:keys [value field type] :as props-input}]
   (let [focus (r/atom false)
         id    (str (random-uuid))
         local (r/atom value)
         [valid-fn parse-fn] (apply set-format [field type])]
     (fn [{:keys [label value focus-extra-class on-change] :as props-input}]
       (let [props (dissoc props-input :label :focus-extra-class :type :field)]
         [:div.mdc-text-field.mdc-text-field--upgraded {:class (when @focus (str "mdc-text-field--focused" focus-extra-class))}
          [:input.mdc-text-field__input (merge props {:id id
                                                      :type "text"
                                                      :on-change #(when @focus (do
                                                                                 (reset! local (-> % .-target .-value valid-fn))
                                                                                 (on-change (parse-fn @local))))
                                                      :value @local
                                                      :on-focus  #(reset! focus true)
                                                      :on-blur #(do
                                                                  (reset! focus false)
                                                                  (on-change (parse-fn @local)))})]
          [:label.mdc-floating-label {:for id
                                      :class (when (or (not (blank? (str value))) @focus) "mdc-floating-label--float-above")}
           label]
          [:div.mdc-line-ripple {:class (when @focus "mdc-line-ripple--active")}]])))))



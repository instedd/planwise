(ns planwise.client.sources
  (:require [re-frame.core :as rf]
            [re-frame.core :refer [dispatch subscribe]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common2 :as common2]
            [planwise.client.utils :as utils]
            [planwise.client.ui.rmwc :as m]))

(def in-sources (rf/path [:sources]))

;(def initial-db
;  {:list (asdf/new nil)})

;; ----------------------------------------------------------------------------
;; API methods

;(def load-sources
;  {:method    :get
;   :section   :show
;   :uri       "/api/sources"})

;; ----------------------------------------------------------------------------
;; Listing sources

;(rf/reg-event-fx
; :sources/load
; in-sources
; (fn [{:keys [db]} [_]]
;   {:api (assoc load-sources
;                :on-success [:sources/loaded])
;    :db  (update db :list asdf/reload!)}))
;
;(rf/reg-event-db
; :sources/loaded
; in-sources
; (fn [db [_ sources]]
;   (update db :list asdf/reset! sources)))

;; ----------------------------------------------------------------------------
;; Subs

;(rf/reg-sub
; :sources/list
; (fn [db _]
;   (get-in db [:sources :list])))

(rf/reg-sub
 :sources/dropdown-options
 (fn [db _]
   (let [list (asdf/value (get-in db [:sources :list]))]
     (mapv (fn [source] (let [{:keys [id name]} source] {:value id :label name})) list))))

;; ----------------------------------------------------------------------------
;; Views

(defn- disabled-input-component
  [{:keys [label value options empty-label]}]
  [common2/text-field {:type     "text"
                       :label    label
                       :value    (utils/label-from-options options value empty-label)
                       :disabled true}])

(defn- sources-select-component
  [{:keys [label value options empty-label on-change]}]
  [m/Select {:label (if (empty? options) empty-label label)
             :disabled (empty? options)
             :value (str value)
             :options options
             :onChange #(on-change (js/parseInt (-> % .-target .-value)))}])

(defn sources-dropdown-component
  [{:keys [label value on-change disabled?]}]
  (let [list      (subscribe [:sources/list])
        options   (subscribe [:sources/dropdown-options])
        component (if (or disabled? (empty? @options))
                    disabled-input-component
                    sources-select-component)]
    (when (asdf/should-reload? @list)
      (dispatch [:sources/load]))
    [component {:label        label
                :value        value
                :options      @options
                :empty-label  "No sources layer available."
                :on-change    on-change}]))

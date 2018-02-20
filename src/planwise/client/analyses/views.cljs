(ns planwise.client.analyses.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common :as common]))


;; ----------------------------------------------------------------------------
;; Analyses list

(defn new-analysis-button
  []
  [:button.primary
   {:on-click
    #(dispatch [:analyses/create-analysis!])}
   "New Analysis"])

(defn no-analyses-view []
  [:div.empty-list
   [common/icon :box]
   [:p "You have no analyses yet"]
   [:div
    [new-analysis-button]]])

(defn analysis-card
  [analysis]
  (let [name (:name analysis)]
    [:div.analysis-card
     [:h1 name]]))

(defn analyses-list
  [analyses]
  [:div
   [new-analysis-button]
   [:ul.analyses-list
    (for [analysis analyses]
      [:li {:key (:id analysis)}
       [analysis-card analysis]])]])

(defn analyses-page
  []
  (let [analyses (subscribe [:analyses/list])]
    (fn []
      (let [items (asdf/value @analyses)]
        (when (asdf/should-reload? @analyses)
          (dispatch [:analyses/load-analyses]))
        [:article.analyses
         (cond
           (nil? items) [common/loading-placeholder]
           (empty? items) [no-analyses-view]
           :else [analyses-list items])]))))

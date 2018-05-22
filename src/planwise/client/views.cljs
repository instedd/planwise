(ns planwise.client.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.config :as config]
            [planwise.client.routes :as routes]
            [planwise.client.components.common :refer [icon]]
            [planwise.client.components.common2 :as common2]
            [planwise.client.projects2.views :as projects2]
            [planwise.client.providers-set.views :as providers-set]
            [planwise.client.sources.views :as sources]
            [planwise.client.scenarios.views :as scenarios]
            [planwise.client.design.views :as design]))


(def current-user-email
  (atom config/user-email))

(defn signout-button
  []
  [:button.signout
   {:type :button
    :on-click #(dispatch [:signout])}
   [icon :signout "icon-small"]])

(defmulti content-pane identity)

(defmethod content-pane :home []
  ;; :home is rendered on for all the routes initially
  (if (= js/window.location.pathname "/")
    [common2/redirect-to (routes/projects2)]
    [common2/loading-placeholder]))

(defmethod content-pane :projects2 []
  [projects2/project2-view])

(defmethod content-pane :providers-set []
  [providers-set/providers-set-page])

(defmethod content-pane :sources []
  [sources/sources-page])

(defmethod content-pane :design []
  [design/app])

(defmethod content-pane :scenarios []
  [scenarios/scenarios-page])

(defn planwise-app []
  (let [current-page (subscribe [:current-page])]
    (fn []
      [content-pane @current-page])))

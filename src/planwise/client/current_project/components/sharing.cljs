(ns planwise.client.current-project.components.sharing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [reagent.core :as r]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.styles :as styles]))

(defn share-dialog []
  (let [view-state (subscribe [:current-project/view-state])
        share-link (subscribe [:current-project/share-link])]
    (fn []
      (let [close-fn #(dispatch [:current-project/close-share-dialog])
            key-handler-fn #(when (= 27 (.-which %)) (close-fn))
            select-text-fn #(.select (.-target %))]
        [:div.dialog.share
         {:on-key-down key-handler-fn}
         [:div.title
          [:h1 "Share Project"]
          [common/close-button {:on-click close-fn}]]

         [:div.form-control
          [:label "Sharing link"]
          [:input.share-link  {:read-only true
                               :value @share-link
                               :on-click select-text-fn}]
          [:a {:href "#"
               :title "Reset the sharing link"
               :on-click (utils/with-confirm
                           #(dispatch [:current-project/reset-share-token])
                           (str "Are you sure you want to reset the sharing link?\n\n"
                                 "Anyone who has not yet opened the link will not "
                                 "be able to access, and will need to be shared the new "
                                 "link."))}
            (common/icon :refresh "icon-medium")]]

         [:div.actions
          [:button.cancel
           {:type "button"
            :on-click close-fn}
           "Close"]]]))))

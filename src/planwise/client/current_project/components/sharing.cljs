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

(defn- search-box
  []
  (let [search-string (subscribe [:current-project/shares-search-string])]
    (fn []
      [:div.small-search-box
       [:input
        {:type "search"
         :placeholder "Search"
         :value @search-string
         :on-change (utils/dispatch-value-fn :current-project/search-shares)}]])))

(defn share-dialog []
  (let [view-state (subscribe [:current-project/view-state])
        share-link (subscribe [:current-project/share-link])
        project-shares (subscribe [:current-project/shares])
        filtered-shares (subscribe [:current-project/filtered-shares])
        emails-text (subscribe [:current-project/sharing-emails-text])
        send-emails-action (subscribe [:current-project/sharing-send-emails])
        share-token-state (subscribe [:current-project/sharing-token-state])]
    (fn []
      (let [close-fn #(dispatch [:current-project/close-share-dialog])
            key-handler-fn #(when (= 27 (.-which %)) (close-fn))
            select-text-fn #(.select (.-target %))
            reset-share-token-fn (utils/with-confirm
                                   #(dispatch [:current-project/reset-share-token])
                                   (str "Are you sure you want to reset the sharing link?\n\n"
                                        "If you reset the link, anyone who has not yet accessed "
                                        "the project will not be able to do it until you send them "
                                        "the new link."))]
        [:div.dialog.share
         {:on-key-down key-handler-fn}
         [:div.title
          [:h1 "Share Project"]
          [common/close-button {:on-click close-fn}]]

         [:div.form-control
          [:label "Sharing link"]
          [:input.share-link  {:read-only true
                               :value (when-not (= :reloading @share-token-state) @share-link)
                               :placeholder "Loading..."
                               :on-click select-text-fn}]
          [:button.secondary  {:title "Reset the sharing link"
                               :on-click reset-share-token-fn
                               :disabled (= :reloading @share-token-state)}
            (common/icon :refresh "icon-medium")]]

         (when (seq (asdf/value @project-shares))
          [:div.shares
           [:p (str (utils/pluralize (count (asdf/value @project-shares)) "user has" "users have") " access to this project")]
           [search-box]
           [:ul
            (for [{:keys [user-id user-email]} @filtered-shares]
             [:li {:key user-id}
              [:span user-email]
              [:button.secondary  {:title "Remove access for this user"
                                   :on-click #(dispatch [:current-project/delete-share user-id])}
                (common/icon :close "icon-small")]])]])

         [:form {:on-submit (utils/prevent-default #(dispatch [:current-project/send-sharing-emails]))}
          [:div.form-control.relative
           (common/icon :mail-outline "icon-input-placeholder")
           [:input.send-email.with-icon {:placeholder "Send sharing link via email"
                                         :disabled (not (:editable @send-emails-action))
                                         :value @emails-text
                                         :class (when-not (:valid @send-emails-action) "error")
                                         :on-change (utils/dispatch-value-fn :current-project/reset-sharing-emails-text)}]]

          [:div.actions
           [:button.primary
             {:type "submit"
              :disabled (:disabled @send-emails-action)
              :title (:title @send-emails-action)}
             (:label @send-emails-action)]
           [:button.cancel
             {:type "button"
              :on-click close-fn}
             "Close"]]]]))))

(ns planwise.client.current-project.components.sharing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [reagent.core :as r]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.styles :as styles]
            [planwise.client.utils :as utils]
            [planwise.client.current-project.db :as db]))

(defn- share-link-control []
  (let [share-link (subscribe [:current-project/share-link])]
    (fn []
      (let [select-text-fn #(.select (.-target %))
            reset-share-token-fn (utils/with-confirm
                                   #(dispatch [:current-project/reset-share-token])
                                   (str "Are you sure you want to reset the sharing link?\n\n"
                                        "If you reset the link, anyone who has not yet accessed "
                                        "the project will not be able to do it until you send them "
                                        "the new link."))]
        [:div.form-control
         [:label "Sharing link"]
         [:input.share-link  {:read-only true
                              :value (when (asdf/valid? @share-link) (asdf/value @share-link))
                              :placeholder "Loading..."
                              :on-click select-text-fn}]
         [:button.secondary  {:title "Reset the sharing link"
                              :on-click reset-share-token-fn
                              :disabled (not (asdf/valid? @share-link))}
          (common/icon :refresh "icon-medium")]]))))

(defn- search-box []
  (let [search-string (subscribe [:current-project/shares-search-string])]
    (fn []
      [:div.small-search-box
       [:input
        {:type "search"
         :placeholder "Search"
         :value @search-string
         :on-change (utils/dispatch-value-fn :current-project/search-shares)}]])))

(defn- project-shares-list []
  (let [project-shares (subscribe [:current-project/shares])
        filtered-shares (subscribe [:current-project/filtered-shares])]
    (fn []
      (let [shares (asdf/value @project-shares)
            shares-valid? (asdf/valid? @project-shares)]
        (when (asdf/should-reload? @project-shares)
          (dispatch [:current-project/load-project-shares]))
        (when (seq shares)
          [:div.shares
           [:p (str (utils/pluralize (count shares) "user has" "users have") " access to this project")]
           [search-box]
           [:ul
            (for [{:keys [user-id user-email]} @filtered-shares]
              [:li {:key user-id}
               [:span user-email]
               (when shares-valid?
                 [:button.secondary  {:title "Remove access for this user"
                                      :on-click #(dispatch [:current-project/delete-share user-id])}
                  (common/icon :close "icon-small")])])]])))))

(defn- send-emails-form []
  (let [emails-text-sub  (subscribe [:current-project/sharing-emails-text])
        emails-state-sub (subscribe [:current-project/sharing-emails-state])]
    (fn []
      (let [close-fn       #(dispatch [:current-project/close-share-dialog])
            emails-state   @emails-state-sub
            emails-list    (db/split-emails @emails-text-sub)
            valid-emails   (filter utils/is-valid-email? emails-list)
            invalid-emails (filter (complement utils/is-valid-email?) emails-list)
            button-title   (cond
                             (= :sending emails-state) "Sending..."
                             (= :sent emails-state)    "Emails successfully sent"
                             (empty? emails-list)      "Enter one or more email addresses"
                             (seq invalid-emails)      (str
                                                        (str/join ", " invalid-emails) " "
                                                        (if (= 1 (count invalid-emails))
                                                          "is an invalid email address"
                                                          "are invalid email addresses"))
                             :else                     "Send this project sharing link to the emails above")
            button-label   (cond
                             (= :sending emails-state) "Sending..."
                             (= :sent emails-state)    "Sent"
                             (seq valid-emails)        (str "Send to " (utils/pluralize (count valid-emails) "user"))
                             :else                     "Send")]


        [:form {:on-submit (utils/prevent-default #(dispatch [:current-project/send-sharing-emails]))}
         [:div.form-control.relative
          (common/icon :mail-outline "icon-input-placeholder")
          [:input.send-email.with-icon {:placeholder "Send sharing link via email"
                                        :disabled (= :sending emails-state)
                                        :value @emails-text-sub
                                        :class (when (seq invalid-emails) "error")
                                        :on-change (utils/dispatch-value-fn :current-project/reset-sharing-emails-text)}]]
         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (or (#{:sending :sent} emails-state) (empty? emails-list) (seq invalid-emails))
            :title button-title}
           button-label]
          [:button.cancel
           {:type "button"
            :on-click close-fn}
           "Close"]]]))))

(defn share-dialog []
  (let [view-state (subscribe [:current-project/view-state])]
    (fn []
      (let [close-fn #(dispatch [:current-project/close-share-dialog])
            key-handler-fn #(when (= 27 (.-which %)) (close-fn))]

        [:div.dialog.share
         {:on-key-down key-handler-fn}
         [:div.title
          [:h1 "Share Project"]
          [common/close-button {:on-click close-fn}]]

         [share-link-control]
         [project-shares-list]
         [send-emails-form]]))))

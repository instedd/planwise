(ns planwise.client.current-project.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]
            [planwise.client.routes :as routes]
            [planwise.client.current-project.db :as db]
            [planwise.client.mapping :as mapping]
            [planwise.client.utils :as utils]))


;; ----------------------------------------------------------------------------
;; Current project subscriptions

(register-sub
 :current-project/loaded?
 (fn [db [_]]
   (reaction (= (get-in @db [:current-project :project-data :id])
                (js/parseInt (get-in @db [:page-params :id]))))))

(register-sub
 :current-project/current-data
 (fn [db [_]]
   (reaction (get-in @db [:current-project :project-data]))))

(register-sub
 :current-project/read-only?
 (fn [db [_]]
   (let [current-data (subscribe [:current-project/current-data])]
     (reaction (:read-only @current-data)))))

(register-sub
 :current-project/shares
 (fn [db [_]]
   (reaction (get-in @db [:current-project :shares]))))

(register-sub
 :current-project/shares-search-string
 (fn [db [_]]
   (reaction (get-in @db [:current-project :sharing :shares-search-string]))))

(register-sub
 :current-project/filtered-shares
 (fn [db [_]]
   (let [search-string (subscribe [:current-project/shares-search-string])
         shares (subscribe [:current-project/shares])]
     (reaction
       (filterv #(gstring/caseInsensitiveContains (:user-email %) (or @search-string "")) (asdf/value @shares))))))

(register-sub
 :current-project/share-link
 (fn [db [_]]
   (let [current-data (subscribe [:current-project/current-data])]
     (reaction
       (str
         (.-origin js/document.location)
         (routes/project-access {:id    (:id @current-data)
                                 :token (:share-token @current-data)}))))))

(register-sub
 :current-project/filter-definition
 (fn [db [_ filter]]
   (reaction (get-in @db [:current-project :filter-definitions filter]))))

(register-sub
 :current-project/facilities
 (fn [db [_ data]]
   (case data
     :facilities (reaction (get-in @db [:current-project :facilities :list]))
     :isochrones (reaction (get-in @db [:current-project :facilities :isochrones]))
     :filters (reaction (get-in @db [:current-project :project-data :filters :facilities]))
     :stats   (reaction (-> (get-in @db [:current-project :project-data :stats])
                            (select-keys [:facilities-targeted :facilities-total]))))))

(register-sub
 :current-project/facilities-by-type
 (fn [db [_ data]]
   (let [facilities (reaction (get-in @db [:current-project :facilities :list]))
         types      (subscribe [:current-project/filter-definition :facility-type])]
     (reaction
      (->> @facilities
           (group-by :type-id)
           (map (fn [[type-id fs]]
                  (let [type (->> @types
                                  (filter #(= type-id (:value %)))
                                  (first))]
                    [type fs])))
           (sort-by (fn [[type fs]]
                      (count fs)))
           (reverse))))))

(register-sub
 :current-project/facilities-criteria
 (fn [db [_]]
   (reaction (db/facilities-criteria (get-in @db [:current-project])))))

(register-sub
 :current-project/transport-time
 (fn [db [_]]
   (reaction (get-in @db [:current-project :project-data :filters :transport :time]))))

(register-sub
 :current-project/demand-map-key
 (fn [db [_]]
   (reaction (get-in @db [:current-project :project-data :demand-map-key]))))

(register-sub
 :current-project/map-state
 (fn [db [_]]
   (reaction (get-in @db [:current-project :map-state :current]))))

(register-sub
 :current-project/map-view
 (fn [db [_ field]]
   (let [map-view (reaction (get-in @db [:current-project :map-view]))
         current-region-id (reaction (get-in @db [:current-project :project-data :region-id]))
         current-region-max-population (reaction (get-in @db [:current-project :project-data :region-max-population]))
         current-region (reaction (get-in @db [:regions @current-region-id]))]
     (reaction
       (case field
         :position (or
                     (:position @map-view)
                     (mapping/bbox-center (:bbox @current-region))
                     (:position db/initial-position-and-zoom))
         :zoom (or
                 (:zoom @map-view)
                 (+ 4 (:admin-level @current-region))
                 (:zoom db/initial-position-and-zoom))
         :bbox (:bbox @current-region)
         :legend-max @current-region-max-population)))))

(register-sub
 :current-project/map-geojson
 (fn [db [_]]
   (let [current-region-id (reaction (get-in @db [:current-project :project-data :region-id]))]
     (reaction (get-in @db [:regions @current-region-id :geojson])))))

(register-sub
 :current-project/view-state
 (fn [db [_]]
   (reaction (get-in @db [:current-project :view-state]))))

(register-sub
 :current-project/sharing-emails-text
 (fn [db [_]]
   (reaction (get-in @db [:current-project :sharing :emails-text]))))

(register-sub
 :current-project/sharing-send-emails
 (fn [db [_ field]]
   (let [emails-text (subscribe [:current-project/sharing-emails-text])]
     (reaction
       (let [emails-list    (db/split-emails @emails-text)
             state          (get-in @db [:current-project :sharing :state])
             has-emails     (seq emails-list)
             valid-emails   (filter utils/is-valid-email? emails-list)
             invalid-emails (filter (complement utils/is-valid-email?) emails-list)]
         {:editable (not (#{:sending} state))
          :disabled (or (#{:sending :sent} state) (not has-emails) (seq invalid-emails))
          :valid    (not (seq invalid-emails))
          :title    (cond
                      (= :sending state)   "Sending..."
                      (= :sent state)      "Emails successfully sent"
                      (not has-emails)     "Enter one or more email addresses"
                      (seq invalid-emails) (str
                                              (string/join ", " invalid-emails) " "
                                              (if (= 1 (count invalid-emails))
                                                "is an invalid email address"
                                                "are invalid email addresses"))
                      :else                "Send this project sharing link to the emails above")
          :label    (cond
                      (= :sending state) "Sending..."
                      (= :sent state)    "Sent"
                      (seq valid-emails) (str "Send to " (utils/pluralize (count valid-emails) "user"))
                      :else              "Send")})))))

(register-sub
 :current-project/sharing-token-state
 (fn [db [_]]
   (reaction (get-in @db [:current-project :sharing :token-state]))))

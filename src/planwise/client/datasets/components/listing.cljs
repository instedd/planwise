(ns planwise.client.datasets.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.popover :as popover]
            [re-com.core :refer-macros [handler-fn]]
            [clojure.string :as string]
            [planwise.client.utils :as utils]
            [planwise.client.components.common :as common]
            [planwise.client.components.progress-bar :as progress-bar]
            [planwise.client.datasets.db :as db]
            [planwise.client.datasets.components.status-helpers :refer [dataset->status-class
                                                                        dataset->status-icon]]
            [planwise.client.utils :refer [format-percentage]]
            [re-frame.utils :as c]))

(defn new-dataset-button
  []
  [:button.primary
   {:on-click
    #(dispatch [:datasets/begin-new-dataset])}
   "New Dataset"])

(defn search-box
  [dataset-count]
  (let [search-string (subscribe [:datasets/search-string])]
    (fn [dataset-count]
      [:div.search-box
       [:div (utils/pluralize dataset-count "dataset")]
       [:input
        {:type "search"
         :placeholder "Search datasets..."
         :value @search-string
         :on-change #(dispatch [:datasets/search (-> % .-target .-value str)])}]
       [new-dataset-button]])))

(defn no-datasets-view []
  [:div.empty-list
   [common/icon :box]
   [:p "You have no datasets yet"]
   [:div
    [new-dataset-button]]])

(defn- server-status->string
  [server-status]
  (case (:status server-status)
    (nil :ready :done)
    "Ready to use"

    :importing
    (let [progress (:progress server-status)
          progress (when progress (str " " (format-percentage progress)))]
      (case (:state server-status)
        :start "Waiting to start"
        :importing-types "Importing facility types"
        (:request-sites :importing-sites) (str "Importing sites from Resourcemap" progress)
        (:processing-facilities) (str "Pre-processing facilities" progress)
        (:update-projects :updating-projects) "Updating projects"
        "Importing..."))

    :cancelling
    "Cancelling..."

    :unknown
    "Unknown server status"))

(defn- import-result->string
  [result warnings-count]
  (let [text (case result
              :success "Ready to use"
              :cancelled "Import cancelled"
              :import-types-failed "Import failed, error while importing facility types"
              :import-sites-failed "Import failed, error while importing sites from ResourceMap"
              :update-projects-failed "Import failed, error while updating related projects"
              :unexpected-event "Import failed"
              nil)]
    (case result
      (:success :cancelled) (str text ", with " (utils/pluralize warnings-count "warning"))
      text)))

(defn- status-string
  [{status :status, :as server-status} result warnings-count]
  (if (or (nil? status) (#{:ready :done} status))
    (import-result->string result warnings-count)
    (server-status->string server-status)))

(defn dataset-card
  [dataset]
  (let [showing-warnings? (r/atom false)]
    (fn [{:keys [id name description facility-count project-count server-status import-result] :as dataset}]
      (let [server-state (db/server-status->state server-status)
            importing? (db/dataset-importing? server-state)
            cancelling? (db/dataset-cancelling? server-state)
            result (keyword (:result import-result))
            {no-regions :facilities-outside-regions-count
             no-road-network :facilities-without-road-network-count
             no-location :sites-without-location-count
             no-type :sites-without-type-count} import-result
            warnings-count (+ no-regions no-road-network no-location no-type)]
        [:div.dataset-card
         [:h1 name]
         [:h2 description]
         [popover/popover-anchor-wrapper
          :showing? showing-warnings?
          :position :below-left
          :anchor [:p.dataset-status
                   {:class (dataset->status-class dataset)
                    :on-mouse-over (handler-fn (reset! showing-warnings? (pos? warnings-count)))
                    :on-mouse-out (handler-fn (reset! showing-warnings? false))}
                   [common/icon (dataset->status-icon dataset) "icon-small"]
                   (utils/pluralize facility-count "facility" "facilities")
                   (when-let [status (status-string server-status result warnings-count)]
                    (str " (" status ")"))]
          :popover [popover/popover-content-wrapper
                    :showing? showing-warnings?
                    :position :below-right
                    :body [:ul
                           ; Alternative (longer) texts
                           ; "site was not imported since it does not" "sites were not imported since they do not" "have a location defined in ResourceMap"
                           ; "facility is" "facilities are" "outside the supported regions and will not be visible in any project"
                           ; "facility is" "facilities are" "too far from the closest road and will not be evaluated for coverage"
                           (let [warns [[no-location     "site has no" "sites have no" "location defined in ResourceMap"]
                                        [no-type         "site has no" "sites have no" "type defined in ResourceMap"]
                                        [no-regions      "facility is" "facilities are" "outside the supported regions"]
                                        [no-road-network "facility is" "facilities are" "too far from the closest road"]]]
                             (for [[[count singular plural description] index] (zipmap warns (range)) :when (pos? count)]
                               [:li {:key index} (string/join " " [(utils/pluralize count singular plural) description])]))]]]
         (when importing?
           [progress-bar/progress-bar (db/import-progress server-status)])

         (cond
           importing?
           [:div.bottom-right
            [:button.danger
             {:type :button
              :on-click #(dispatch [:datasets/cancel-import! id])
              :disabled cancelling?}
             (if cancelling? "Cancelling..." "Cancel")]]

           (zero? project-count)
           [:div.bottom-right
            [:button.delete
             {:on-click (utils/with-confirm
                          #(dispatch [:datasets/delete-dataset id])
                          "Are you sure you want to delete this dataset?")}
             (common/icon :delete "icon-small")
             "Delete"]])]))))

(defn datasets-list
  [datasets]
  [:div
   [search-box (count datasets)]
   [:ul.dataset-list
    (for [dataset datasets]
      [:li {:key (:id dataset)}
       [dataset-card dataset]])]])

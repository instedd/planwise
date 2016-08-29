(ns planwise.client.datasets.components.listing
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [re-com.popover :as popover]
            [re-com.core :refer-macros [handler-fn]]
            [clojure.string :as string]
            [planwise.client.utils :as utils]
            [planwise.client.components.common :as common]
            [planwise.client.components.progress-bar :as progress-bar]
            [planwise.client.datasets.db :as db]))


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
   [:img {:src "/images/empty-datasets.png"}]
   [:p "You have no datasets yet"]
   [:div
    [new-dataset-button]]])

(defn dataset-card
  [dataset]
  (let [showing-warnings? (r/atom false)]
    (fn [{:keys [id name description facility-count project-count server-status import-result] :as dataset}]
      (let [state (db/server-status->state server-status)
            importing? (db/dataset-importing? state)
            cancelling? (db/dataset-cancelling? state)
            {no-regions :facilities-outside-regions-count
             no-road-network :facilities-without-road-network-count
             no-location :sites-without-location-count} import-result
            warnings-count (+ no-regions no-road-network no-location)]
        [:div.dataset-card
         [:h1 name]
         [:h2 description]
         [popover/popover-anchor-wrapper
          :showing? showing-warnings?
          :position :below-left
          :anchor [:p.dataset-status
                   {:class (when (pos? warnings-count) "warning")
                    :on-mouse-over (handler-fn (reset! showing-warnings? (pos? warnings-count)))
                    :on-mouse-out (handler-fn (reset! showing-warnings? false))}
                   [common/icon (if (pos? warnings-count) :warning :location) "icon-small"]
                   (utils/pluralize facility-count "facility" "facilities")
                   (str " (" (db/server-status->string server-status) ")")
                   (when (pos? warnings-count)
                     (str " with " (utils/pluralize warnings-count "warning" "warnings")))]
          :popover [popover/popover-content-wrapper
                    :showing? showing-warnings?
                    :position :below-right
                    :body [:ul
                           ; Alternative (longer) texts
                           ; "site was not imported since it does not" "sites were not imported since they do not" "have a location defined in ResourceMap"
                           ; "facility is" "facilities are" "outside the supported regions and will not be visible in any project"
                           ; "facility is" "facilities are" "too far from the closest road and will not be evaluated for coverage"
                           (let [warns [[no-location     "site has no" "sites have no" "location defined in ResourceMap"]
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

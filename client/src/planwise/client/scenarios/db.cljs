(ns planwise.client.scenarios.db
  (:require [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

;; View state possible values
;;
;;                  :get-suggestions-for-new-provider
;;                           ▲  │        │
;;                           │  │        ▼
;;                           │  │  ┌──►:new-provider
;;          ┌─────────────┐  │  │  │     │   ┌────────────┐
;;          │             │  │  ▼  │     ▼   │            │
;;          ▼           ┌─┴──┴─────┴─────────┴─┐          ▼
;; :show-actions-table  │  :current-scenario   │   :search-providers
;;          │           └────┬─────────────────┘          │
;;          │             ▲  │  ▲        ▲   ▲            │
;;          └─────────────┘  │  │        │   └────────────┘
;;                           │  │  :new-intervention
;;                           │  │        ▲
;;                           ▼  │        │
;;                 :get-suggestions-for-improvements


(def initial-db
  {:view-state          :current-scenario
   :open-dialog         nil
   :rename-dialog       nil
   :current-scenario    nil
   :changeset-dialog    {:provider     nil
                         :new-change?  false
                         :reset-state? false}
   :selected-provider   nil
   :selected-suggestion nil
   :coverage-cache      nil
   :list-scope          nil
   :list                (asdf/new nil)
   :sort-column         nil
   :sort-order          nil
   :suggestions         {:request-key  nil
                         :coverages    nil
                         :locations    nil
                         :improvements nil}
   :providers-search    nil})

(defn initial-scenario?
  [scenario]
  (= (:label scenario) "initial"))

(defmulti new-action :action-name)

(defmethod new-action :create
  [props]
  {:action     "create-provider"
   :name       (:name props)
   :investment 0
   :capacity   (:capacity props)
   :location   (:location props)
   :id         (str (random-uuid))})

(defmethod new-action :upgrade
  [props]
  {:action     "upgrade-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})

(defmethod new-action :increase
  [props]
  {:action     "increase-provider"
   :investment 0
   :capacity   0
   :id         (:id props)})

(defn new-provider-from-change
  [change]
  {:id              (:id change)
   :name            (:name change)
   :location        (:location change)
   :matches-filters true
   :change          change})

(defn provider-with-change
  [{:keys [change matches-filters] :as provider}]
  (let [change' (or change
                    (new-action provider (if (not matches-filters) :upgrade :increase)))]
    (assoc provider :change change')))

(defn- apply-change
  [providers-by-id change]
  (if (= (:action change) "create-provider")
    (let [new-provider (new-provider-from-change change)]
      (conj providers-by-id [(:id new-provider) new-provider]))
    (update providers-by-id (:id change) assoc :change change)))

(defn- update-capacity-and-demand
  [data-by-id {:keys [capacity] :as provider}]
  (merge provider
         (select-keys (data-by-id (:id provider))
                      [:capacity
                       :satisfied-demand
                       :unsatisfied-demand
                       :free-capacity
                       :required-capacity
                       :reachable-demand])
         {:initial-capacity capacity}))

(defn- all-providers
  [{:keys [providers disabled-providers changeset providers-data]}]
  (let [providers-by-id         (->> (concat (map #(assoc % :matches-filters true)
                                                  providers)
                                             (map #(assoc % :matches-filters false :capacity 0)
                                                  disabled-providers))
                                     (utils/index-by :id))
        changed-providers-by-id (reduce apply-change providers-by-id changeset)
        data-by-id              (utils/index-by :id providers-data)]
    (map (partial update-capacity-and-demand data-by-id)
         (vals changed-providers-by-id))))

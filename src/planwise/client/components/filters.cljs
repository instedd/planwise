(ns planwise.client.components.filters)

;; Filter checkboxes

(defn- labeled-checkbox [{:keys [value label checked toggle-fn]}]
  (let [elt-id (str "checkbox-" (hash value))
        options (some->> toggle-fn (assoc nil :on-change))]
    [:div
     [:input (assoc options
                    :type "checkbox"
                    :id elt-id
                    :value value
                    :checked checked)]
     [:label {:for elt-id} label]]))

(defn filter-checkboxes [{:keys [options value toggle-fn]}]
  (let [value (set value)]
    (map-indexed (fn [idx option]
                   (let [option-value (:value option)
                         checked (value option-value)
                         callback-fn (when toggle-fn #(toggle-fn option-value))]
                     [labeled-checkbox (assoc option
                                              :key idx
                                              :checked checked
                                              :toggle-fn callback-fn)]))
                 options)))

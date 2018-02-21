(ns planwise.client.components.filters)

;; Filter checkboxes

(defn- labeled-checkbox [{:keys [value label checked disabled toggle-fn decoration-fn], :as option}]
  (let [elt-id (str "checkbox-" (hash value))
        options (when-not disabled (some->> toggle-fn (assoc nil :on-change)))
        decoration (when decoration-fn (decoration-fn option))]
    [:div
     decoration
     [:label {:for elt-id} label]
     [:input (assoc options
                    :type "checkbox"
                    :disabled disabled
                    :id elt-id
                    :checked (not (nil? checked)))]]))

(defn filter-checkboxes [{:keys [options value disabled toggle-fn decoration-fn]}]
  (let [value (set value)]
    (map-indexed (fn [idx option]
                   (let [option-value (:value option)
                         checked (value option-value)
                         callback-fn (when toggle-fn #(toggle-fn option-value))]
                     [labeled-checkbox (assoc option
                                              :key idx
                                              :checked checked
                                              :disabled disabled
                                              :toggle-fn callback-fn
                                              :decoration-fn decoration-fn)]))
                 options)))

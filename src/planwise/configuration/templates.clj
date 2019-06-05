(ns planwise.configuration.templates)

(defn templates-list
  []
  [{:description "Plan facilities based on ground access"
    :icon "directions_walk"
    :key "plan"
    :defaults {:name "ground"
               :config {:coverage {:filter-options {:driving-time 90}}
                        :demographics {:target 12
                                       :unit-name "humans"}
                        :actions {:budget 123123
                                  :build [{:id "build-0"
                                           :capacity 123
                                           :investment 456}
                                          {:id "build-1"
                                           :capacity 333
                                           :investment 444}]
                                  :upgrade [{:id "upgrade-0"
                                             :capacity 123
                                             :investment 456}
                                            {:id "upgrade-1"
                                             :capacity 333
                                             :investment 444}]}}

               :source-set-id 2
               :region-id 1
               :provider-set-id 1}}

   {:description "Plan diagonostic devices & sample referrals"
    :icon "call_split"
    :key "diagnosis"
    :defaults {:name "sample"}}])

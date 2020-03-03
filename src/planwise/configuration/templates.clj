(ns planwise.configuration.templates)

(defn templates-list
  []
  [{:description "Plan facilities based on ground access"
    :icon "directions_walk"
    :key "plan"
    :defaults {:name "Improve the coverage of meningitis rapid test"
               :config {:coverage {:filter-options {:driving-time 90}} ;How to define Region?
                        :demographics {:target 12
                                       :unit-name "suspected cases of meningitis"}
                        :actions {:budget 500000
                                  :build [{:id "build-0"
                                           :capacity 100
                                           :investment 90000}
                                          {:id "build-1"
                                           :capacity 300
                                           :investment 150000}]
                                  :upgrade [{:id "upgrade-0"
                                             :capacity 100
                                             :investment 10000}
                                            {:id "upgrade-1"
                                             :capacity 300
                                             :investment 40000}]}}

               :source-set-id nil
               :region-id nil
               :provider-set-id nil}}
   {:description "Plan diagonostic devices & sample referrals"
    :icon "call_split"
    :key "diagnosis"
    :defaults {:name "sample"
               :config {:coverage {}
                        :demographics {}
                        :actions {}}

               :source-set-id nil
               :region-id nil
               :provider-set-id nil}}
   {:key "empty"
    :defaults {:name ""
               :config {:coverage {}
                        :demographics {}
                        :actions {}}

               :source-set-id nil
               :region-id nil
               :provider-set-id nil}}])

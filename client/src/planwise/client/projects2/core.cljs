(ns planwise.client.projects2.core)

(def sections-base
  [{:step "goal" :title "Goal" :spec :planwise.model.project/goal-step}
   {:step "consumers" :title "Consumers" :spec :planwise.model.project/consumers-step}
   {:step "providers" :title "Providers" :spec :planwise.model.project/providers-step}
   {:step "coverage" :title "Coverage" :spec :planwise.model.project/coverage-step}
   {:step "actions" :title "Actions" :spec :planwise.model.project/actions-step}
   {:step "review" :title "Review" :spec :planwise.model.project/review-step}])

(def sections
  (->> sections-base
       (#(list % (concat (drop 1 %) (repeat nil))))
       (apply mapv (fn [step next]
                     (assoc step :next-step (:step next))))))

(ns planwise.model.import-job-test
  (:require [planwise.model.import-job :as sut]
            [reduce-fsm :as fsm]
            [clojure.test :refer :all]))


(deftest push-task-test
  (is (= [] (sut/push-task [] nil)))
  (is (= [:foo] (sut/push-task [] :foo)))
  (is (= [:foo] (sut/push-task [:foo] :foo)))
  (is (= [:foo] (sut/push-task [:foo] nil)))
  (is (= [:foo :bar] (sut/push-task [:foo] :bar)))
  (is (= [:foo :bar :foo] (sut/push-task [:foo :bar] :foo))))

(deftest remove-task-test
  (is (= [] (sut/remove-task [] nil)))
  (is (= [] (sut/remove-task [:foo] :foo)))
  (is (= [] (sut/remove-task [:foo :foo] :foo)))
  (is (= [:foo] (sut/remove-task [:foo] :bar))))

(deftest task-report?-test
  (is (false? (sut/task-report? nil)))
  (is (true? (sut/task-report? [:success :import-types])))
  (is (true? (sut/task-report? [:failure :import-types])))
  (is (true? (sut/task-report? [:success [:process-facility [1]]])))
  (is (false? (sut/task-report? :next)))
  (is (false? (sut/task-report? :success))))

(deftest last-task-report?-test
  (is (true? (sut/last-task-report? [{:tasks []} [:success :import-types]])))
  (is (true? (sut/last-task-report? [{:tasks [:import-types]} [:success :import-types]])))
  (is (true? (sut/last-task-report? [{:tasks [:import-types]} [:failure :import-types]])))
  (is (false? (sut/last-task-report? [{:tasks [:import-types]} [:success :other-task]])))
  (is (false? (sut/last-task-report? [{:tasks [:process-facility [1]]}
                                      [:success [:process-facility [2]]]]))))

(deftest page-number-mismatch?-test
  (is (true? (boolean (sut/page-number-mismatch? [{:page 1} [:success [:import-sites 2] [1 2 3]]])))))

(deftest fsm-test
  (letfn [(reduce-job [initial events]
            (let [job (reduce fsm/fsm-event initial events)
                  state (:state job)
                  next-task (sut/job-peek-next-task job)
                  result (sut/job-result job)]
              [state next-task result]))]

    ;;
    ;; Import facility types stage
    ;;
    (let [initial (sut/import-job)]
      ;; initial task dispatched
      (is (= (reduce-job initial [:next])         [:importing-types :import-types nil]))
      ;; only one import types dispatched
      (is (= (reduce-job initial [:next :next])   [:importing-types nil nil]))
      ;; immediate user cancellation
      (is (= (reduce-job initial [:cancel])       [:error nil :cancelled]))
      ;; unexpected/invalid event
      (is (= (reduce-job initial [:foo])          [:error nil :unexpected-event]))
      ;; user cancels after the import types task was dispatched
      (is (= (reduce-job initial [:next :cancel]) [:cancelling nil :cancelled]))

      ;; user cancels while importing facility types
      (is (= (reduce-job initial [:next
                                  :cancel
                                  [:success :import-types :new-types]
                                  :next
                                  [:success :update-projects]])
             [:error nil :cancelled]))
      (is (= (reduce-job initial [:next
                                  :cancel
                                  [:failure :import-types :some-error]
                                  :next
                                  [:failure :update-projects]])
             [:error nil :cancelled]))

      ;; failure to import facility types
      (is (= (reduce-job initial [:next [:failure :import-types :some-error]])
             [:error nil :import-types-failed]))

      ;; facility types imported successfully
      (is (= (reduce-job initial [:next [:success :import-types :new-types]])
             [:request-sites nil nil]))

      ;;
      ;; Import sites stage
      ;;
      (let [job         (reduce fsm/fsm-event initial [:next [:success :import-types :new-types]])
            page-result #(merge {:sites-without-location [] :sites-without-type []} %)]
        ;; first page of sites is requested
        (is (= (reduce-job job [:next])
               [:importing-sites [:import-sites 1] nil]))
        ;; single page of sites successfully imported
        (is (= (reduce-job job [:next [:success [:import-sites 1] [nil {:new [1 2 3]} 1]]])
               [:delete-old-facilities nil nil]))
        ;; page of sites successfully imported (continue)
        (is (= (reduce-job job [:next [:success [:import-sites 1] [:continue (page-result {:page-ids {:new [1 2 3]} :total-pages 5})]]])
               [:request-sites nil nil]))
        ;; page report mismatch
        (is (= (reduce-job job [:next [:success [:import-sites 2] [nil {:new [1 2 3]} 4]]])
               [:error [:import-sites 1] :unexpected-event]))
        ;; sites page is incremented
        (is (= (reduce-job job [:next [:success [:import-sites 1] [:continue (page-result {:page-ids {:new [1 2 3]} :total-pages 3})]] :next])
               [:importing-sites [:import-sites 2] nil]))
        ;; second page of sites is imported
        (is (= (reduce-job job [:next [:success [:import-sites 1] [:continue (page-result {:page-ids {:new [1 2 3]} :total-pages 1})]]
                                    :next [:success [:import-sites 2] [nil []]]])
               [:delete-old-facilities nil nil]))

       ;;
       ;; Delete old facilities and types stage (facilities 1,2 to process)
       ;;
       (let [job (reduce fsm/fsm-event job [:next [:success [:import-sites 1] [nil (page-result {:page-ids {:new [1 2]} :total-pages 1})]]])]
         (is (= (reduce-job job [:next])
                [:deleting-old-facilities :delete-old-facilities nil]))
         (is (= (reduce-job job [:next [:success :delete-old-facilities nil]])
                [:delete-old-types nil nil]))
         (is (= (reduce-job job [:next [:success :delete-old-facilities nil] :next])
                [:deleting-old-types :delete-old-types nil]))
         (is (= (reduce-job job [:next [:success :delete-old-facilities nil] :next [:success :delete-old-types nil]])
                [:update-projects nil nil]))

        ;;
        ;; Update projects stage (facilities 1,2 to process)
        ;;
        (let [job (reduce fsm/fsm-event job [:next [:success :delete-old-facilities nil] :next [:success :delete-old-types nil]])]
          (is (= (reduce-job job [:next])
                 [:updating-projects :update-projects nil]))
          (is (= (reduce-job job [:next [:success :update-projects nil]])
                 [:processing-facilities nil nil]))

          ;;
          ;; Process facilities stage (facilities 1,2 to process)
          ;;
          (let [job (reduce fsm/fsm-event job [:next [:success :update-projects nil]])]
            (is (= (reduce-job job [:next])
                   [:processing-facilities [:process-facilities [1]] nil]))
            (is (= (reduce-job job [:next :next])
                   [:processing-facilities [:process-facilities [2]] nil]))
            (is (= (reduce-job job [:next :next :next])
                   [:processing-facilities nil nil]))


            (is (= (reduce-job job [:next [:success [:process-facilities [1]] nil]])
                   [:processing-facilities nil nil]))
            (is (= (reduce-job job [:next [:success [:process-facilities [1]] nil] :next])
                   [:processing-facilities [:process-facilities [2]] nil]))
            (is (= (reduce-job job [:next :next [:success [:process-facilities [1]] nil]])
                   [:processing-facilities nil nil]))
            (is (= (reduce-job job [:next :next
                                    [:success [:process-facilities [1]] nil]
                                    [:success [:process-facilities [2]] nil]])
                   [:done nil :success]))

            ;; cancellations during this stage
            (is (= (reduce-job job [:cancel])
                   [:error nil :cancelled]))
            (is (= (reduce-job job [:next :cancel])
                   [:clean-up-wait nil :cancelled]))
            (is (= (reduce-job job [:next :cancel [:success [:process-facilities [1]] nil]])
                   [:error nil :cancelled])))))

       ;; Alternate path: no facilities were imported
       (let [job (reduce fsm/fsm-event job [:next
                                            [:success [:import-sites 1] [nil (page-result {:page-ids {} :total-pages 1})]]
                                            :next
                                            [:success :delete-old-facilities nil]
                                            :next
                                            [:success :delete-old-types nil]
                                            :next
                                            [:success :update-projects nil]])]
         (is (= (reduce-job job [:next])
                [:done nil :success])))))))

    ;; TODO: test error conditions in all stages
    ;; TODO: test cancellation in all stages

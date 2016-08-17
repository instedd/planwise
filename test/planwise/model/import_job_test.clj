(ns planwise.model.import-job-test
  (:require [planwise.model.import-job :as sut]
            [reduce-fsm :as fsm]
            [clojure.test :refer :all]))


(deftest push-task-test
  (is (= [] (sut/push-task [] nil)))
  (is (= [:foo] (sut/push-task [] :foo)))
  (is (= [:foo] (sut/push-task [:foo] :foo)))
  (is (= [:foo nil] (sut/push-task [:foo] nil)))
  (is (= [:foo :bar] (sut/push-task [:foo] :bar)))
  (is (= [:foo :bar :foo] (sut/push-task [:foo :bar] :foo))))

(deftest peek-task-test
  (is (nil? (sut/peek-task nil)))
  (is (nil? (sut/peek-task [])))
  (is (nil? (sut/peek-task [nil])))
  (is (= :foo (sut/peek-task [:foo]))))

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
      (is (= (reduce-job initial [:next :cancel]) [:cancelling :import-types :cancelled]))

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
             [:request-sites nil nil])))

    (let [initial (reduce fsm/fsm-event (sut/import-job) [:next [:success :import-types :new-types]])]
      ;; first page of sites is requested
      (is (= (reduce-job initial [:next])
             [:importing-sites [:import-sites 1] nil]))
      ;; single page of sites successfully imported
      (is (= (reduce-job initial [:next [:success [:import-sites 1] [nil [1 2 3] 1]]])
             [:processing-facilities nil nil]))
      ;; page of sites successfully imported (continue)
      (is (= (reduce-job initial [:next [:success [:import-sites 1] [:continue [1 2 3] 5]]])
             [:request-sites nil nil]))
      ;; page report mismatch
      (is (= (reduce-job initial [:next [:success [:import-sites 2] [nil [1 2 3] 4]]])
             [:error [:import-sites 1] :unexpected-event]))
      ;; sites page is incremented
      (is (= (reduce-job initial [:next [:success [:import-sites 1] [:continue [1 2 3] 3]] :next])
             [:importing-sites [:import-sites 2] nil]))
      ;; second page of sites is imported
      (is (= (reduce-job initial [:next [:success [:import-sites 1] [:continue [1 2 3] 1]]
                                  :next [:success [:import-sites 2] [nil []]]])
             [:processing-facilities nil nil]))

      ;; TODO: write tests for the rest of the flow
      )
    ))

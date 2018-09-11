(ns planwise.client.test-runner
  (:require [cljs.test :as t :include-macros true]
            [doo.runner :refer-macros [doo-tests]]
            [planwise.client.utils-test]
            [planwise.client.scenarios-test]))

(doo-tests 'planwise.client.utils-test
           'planwise.client.scenarios-test)

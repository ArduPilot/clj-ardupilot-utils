(ns ardupilot-utils.log-parser-test
  (:require [ardupilot-utils.log-reader :refer :all]
            [clojure.java.io :as io]
            [clojure.test :refer :all]))

(deftest large-file
  (testing "Testing a large file of valid input"
    (with-open [bad-input (io/input-stream (io/resource "176.BIN"))]
      (is (= 3886213 (count (parse-bin bad-input)))))))

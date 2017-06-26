(ns ardupilot-utils.log-reader-bench
    (:require [ardupilot-utils.log-reader :refer :all]
              [clojure.java.io :as io])
    (:use perforate.core))

(defgoal parse-bin-file "Parse a bin file")

(defcase parse-bin-file :large-file
  []
  (with-open [stream (io/input-stream (io/resource "176.BIN"))]
    (parse-bin stream)))

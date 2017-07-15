(ns ardupilot-utils.log-analyzer-test
  (:require [ardupilot-utils.log-analysis :refer :all]
            [clojure.java.io :as io]
            [clojure.test :refer :all])
  (:import [java.util.zip ZipEntry]
           [java.util.jar JarFile]))
(deftest large-file
  (testing "Testing a large file of valid input"
           (let [jar (new JarFile (io/file (io/resource "176.jar")))
                 entry (.getEntry jar "176.BIN")]
             (with-open [input (.getInputStream jar entry)]
               (let [results (analyze-log input)]
                 (is (= (count results) 2)))))))



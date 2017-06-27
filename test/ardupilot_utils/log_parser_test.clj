(ns ardupilot-utils.log-parser-test
  (:require [ardupilot-utils.log-reader :refer :all]
            [clojure.java.io :as io]
            [clojure.test :refer :all])
  (:import [java.util.zip ZipEntry]
           [java.util.jar JarFile]))
(deftest large-file
  (testing "Testing a large file of valid input"
           (let [jar (new JarFile (io/file (io/resource "176.jar")))
                 entry (.getEntry jar "176.BIN")]
             (with-open [input (.getInputStream jar entry)]
               (let [parsed (parse-bin input)]
                 (is (instance? clojure.lang.LazySeq parsed))
                 (is (= 3886213 (count parsed))))))))

(deftest filter-accel
  (testing "Ensure that the result is a lazy sequence and that we can filter it"
           (let [jar (new JarFile (io/file (io/resource "176.jar")))
                 entry (.getEntry jar "176.BIN")]
             (with-open [input (.getInputStream jar entry)]
               (is (= 497313 (count (filter #(contains? % :AccY) (parse-bin input)))))))))

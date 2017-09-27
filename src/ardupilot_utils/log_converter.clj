(ns ardupilot-utils.log-converter
    (:require [ardupilot-utils.log-reader :refer [parse-bin]]
              [ardupilot-utils.impl.log-reader :refer [merge-format-message-by-type]]
              [clojure.java.io :as io])
    (:import [java.io InputStream OutputStream]))

(defn bin->csv
  "Convert a binary log file to a csv version.
   Any log corruption, or unexpected ending will be ingored, and the parser
   will attempt to recover parsing the stream"
  [^InputStream input ^OutputStream output]
  (let [reduce-out
  (reduce (fn [format-keys {:keys [message-type] :as message}]
            (if (= message-type :FMT)
              (do
                (.write output (.getBytes "FMT,"))
                (.write output (.getBytes ^String (:Name message)))
                (.write output (int \.))
                (.write output (.getBytes ^String (:Columns message)))
                (.write output (int \newline))
                (merge-format-message-by-type format-keys message))
              (do
                (when-let [{:keys [fields]} (get format-keys message-type)]
                  (.write output (.getBytes (name message-type)))
                  (doseq [field fields]
                    (.write output (int \,))
                    (.write output (.getBytes (str (get message (:name field))))))
                  (.write output (int \newline)))
                format-keys)))
          {} (parse-bin input)) ]
  (.flush output)
  reduce-out)
  )

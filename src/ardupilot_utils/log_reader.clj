(ns ardupilot-utils.log-reader
    (:require [ardupilot-utils.impl.log-reader :refer [find-next-message FORMAT-MESSAGE-ID
                                                       merge-format-message read-field]]
              [clojure.java.io :as io])
    (:import [com.google.common.io LittleEndianDataInputStream]
             [java.io EOFException]))

(defn parse-bin
  "Parse a bin file returning a map of messages to values.
   Any log corruption, or unexpected ending will be ingored, and the parser
   will attempt to recover parsing the stream"
  [input]
  (with-open [reader (new LittleEndianDataInputStream input)]
    (loop [message-id (find-next-message reader)
           formats {128 {:length 89
                         :name "FMT"
                         :fields [{:name :Type    :type \B}
                                  {:name :Length  :type \B}
                                  {:name :Name    :type \n}
                                  {:name :Format  :type \N}
                                  {:name :Columns :type \Z}]}}
           messages (transient [])]
          (if message-id
            (let [{:keys [fields] :as message-format} (get formats message-id)]
              (when-not message-format
                (throw (ex-info "Unknown message format" {:message-id message-id})))
              (if-let [message (try
                                   ; attempt to read all the fields out of the message
                                   (persistent! (reduce (fn [read-fields {:keys [name type]}]
                                                            (assoc! read-fields name (read-field type reader)))
                                                        (transient {}) fields))
                                   (catch EOFException e))]
                (if (= message-id FORMAT-MESSAGE-ID)
                  (recur (find-next-message reader) (merge-format-message formats message) (conj! messages message))
                  (recur (find-next-message reader) formats (conj! messages message)))
                (persistent! messages)))
            (persistent! messages)))))

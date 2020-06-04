(ns ardupilot-utils.tlog
    (:require [clojure.core.async :as async]
              [clojure.java.io :as io]
              [mavlink.core :as mavlink])
    (:import [java.io InputStream]))

(defn- get-parse-options
  []
  {:xml-sources (mapv (fn[m] {:xml-file m
                              :xml-source (io/input-stream (io/resource m))})
                      ["ardupilotmega.xml" "common.xml" "icarous.xml" "uAvionix.xml"])})
(defonce mavlink-info (delay (let [parse-options (get-parse-options)
                                   info (mavlink/parse parse-options)]
                               (doseq [{:keys [xml-source]} (:xml-sources parse-options)]
                                 (.close ^InputStream xml-source))
                               info)))
(defn parse-log
  [log]
  (let [decoded-tlog-ch (async/chan 100)
        tlog-stream (io/input-stream log)]
    (mavlink/open-channel @mavlink-info
                          {:protocol :mavlink1
                           :system-id 1
                           :component-id 190
                           :link-id 0
                           :input-is-tlog? true
                           :signing-options {:accept-message-handler (fn [_] true)}
                           :decode-input-stream tlog-stream
                           :decode-output-channel decoded-tlog-ch
                           :encode-input-channel (async/chan (async/sliding-buffer 1))
                           :encode-output-link (async/chan (async/sliding-buffer 1))
                           :report-error #(println "error reading tlog" %)
                           :exception-handler #(println % "error decoding tlog stream")
                           })
    (letfn [(decode []
              (let [msg (async/<!! decoded-tlog-ch)]
                (if (= :SigningTuples (:message'id msg))
                  (do
                    (.close tlog-stream)
                    (async/close! decoded-tlog-ch)
                    nil)
                  (lazy-seq (cons msg (decode)))))) ]
      (decode))))

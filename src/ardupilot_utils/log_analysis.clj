(ns ardupilot-utils.log-analysis
    (:require [ardupilot-utils.log-reader :refer [parse-bin]]))

(defmacro def-log-test
  "Creates a test definition"
  [name test-fn summarize-fn initial-state]
  `(def ~(with-meta name {})
     {:name ~(keyword name)
      :test-fn ~test-fn
      :summarize-fn ~summarize-fn
      :initial-state ~initial-state}))

(def-log-test power-test
  (fn [state {:keys [message-type] :as message}]
    (if (= message-type :POWR)
      (let [vcc (:Vcc message)
            {:keys [vcc-min vcc-max]} state]
        (assoc state
               :vcc-min (min vcc vcc-min)
               :vcc-max (max vcc vcc-max)))
      state))
  (fn [{:keys [vcc-min vcc-max]}]
      (when (pos? vcc-max) ; positive vcc-max indicates we got at least one message
        (let [vcc-diff (- vcc-max vcc-min)
            diff-threshold 0.3
            minimum-threshold 4.6
            maximum-threshold 5.7]
            (conj []
                  (when (> vcc-diff diff-threshold)
                    {:result :fail
                     :sub-test :vcc-difference
                     :reason (format "Vcc min/max difference of %.3fv, should be < %.1fv"
                                     vcc-diff diff-threshold)
                     :vcc-min vcc-min
                     :vcc-max vcc-max})
                  (when (< vcc-min minimum-threshold)
                    {:result :fail
                     :sub-test :vcc-minimum
                     :reason (format "Vcc below minimum of %.1fv, lowest read values was %.3fv"
                                     minimum-threshold vcc-min)
                     :vcc-min vcc-min})
                  (when (> vcc-max maximum-threshold)
                    {:result :fail
                     :sub-test :vcc-maximum
                     :reason (format "Vcc above the maximum %.1fv, highest read values was %.3fv"
                                     maximum-threshold vcc-max)
                     :vcc-max vcc-max})
                  ))))
  {:vcc-min Float/MAX_VALUE
   :vcc-max 0})

(defn analyze-log
  "Runs a selection of tests over a DF log."
  ([stream] (analyze-log stream [power-test]))
  ([stream tests]
   (remove empty?
           (flatten
             (mapv
               (fn [{:keys [state summarize-fn]}]
                   (summarize-fn state))
               (reduce (fn [tests message]
                           (mapv (fn [{:keys [state test-fn] :as test}]
                                     (assoc test :state (test-fn state message))) tests))
                       (mapv #(assoc % :state (:initial-state %)) tests)
                       (parse-bin stream)))))))

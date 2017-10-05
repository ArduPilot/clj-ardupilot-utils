(ns ardupilot-utils.log-analysis
    (:require [ardupilot-utils.coordinates :refer [haversine]]
              [ardupilot-utils.log-reader :refer [parse-bin]]))

(defmacro def-log-test
  "Creates a test definition"
  [name test-fn summarize-fn initial-state]
  `(def ~(with-meta name {})
     {:name ~(keyword name)
      :test-fn ~test-fn
      :summarize-fn ~summarize-fn
      :initial-state ~initial-state}))

(def-log-test deepstall-test
  (fn [{:keys [stage] :as state} {:keys [message-type] :as message}]
    (case stage
      :normal-flight (case message-type
                       :ATT (assoc! state :pitch (:Pitch message))
                       :PIDL (if (and (:entry-pos state)
                                      (or (zero? (:Des message))
                                          (< (:RelHomeAlt (:entry-pos state)) 30.0)))
                               state
                               (assoc! state :stage :flare))
                       :POS (assoc! state :entry-pos message)
                       :NKF2 (assoc! state :entry-wind message)
                       state)
      :flare (case message-type
               :ATT (assoc! state :pitch (:Pitch message))
               :IMU (if (and (< (:AccZ message) -9.0) (neg? (:pitch state)) (:flare-pos state))
                      (assoc! state :stage :travel)
                      state)
               :POS (assoc! state :flare-pos message)
               state)
      :travel (case message-type
                :IMU (if (and (> (Math/abs (double (:AccZ message))) 15.0)
                              (:impact-pos state))
                       (assoc! state :stage :complete)
                       state)
                :POS (assoc! state :impact-pos message)
                state)
      :complete (case message-type
                  ; require a PIDL message to indicate that we are still in deepstall, and it was not an aborted landing
                  :PIDL (let [{:keys [entry-pos flare-pos impact-pos entry-wind]} state
                              travel-time (/ (- (:TimeUS impact-pos) (:TimeUS flare-pos)) 1000000.0)
                              travel-distance (haversine {:latitude (:Lat flare-pos) :longitude (:Lng flare-pos)}
                                                         {:latitude (:Lat impact-pos) :longitude (:Lng impact-pos)})
                              entry-wind-e (:VWE entry-wind)
                              entry-wind-n (:VWN entry-wind)
                              wind-speed (Math/sqrt (+ (* entry-wind-e entry-wind-e) (* entry-wind-n entry-wind-n)))]
                          (transient {:stage :normal-flight
                                      :results (conj (:results state)
                                                     {:wind-speed wind-speed
                                                      :flare-distance (haversine {:latitude (:Lat entry-pos) :longitude (:Lng entry-pos)}
                                                                                 {:latitude (:Lat flare-pos) :longitude (:Lng flare-pos)})
                                                      :travel-distance travel-distance
                                                      :travel-time travel-time
                                                      :v-down (/ (- (:RelHomeAlt flare-pos) (:RelHomeAlt impact-pos)) travel-time)
                                                      :v-forward (+ wind-speed (/ travel-distance travel-time))
                                                      :entry-pos entry-pos
                                                      :flare-pos flare-pos
                                                      :impact-pos impact-pos})}))
                  state)
      (throw (ex-info "Deepstall test hit unknown stage"
                      {:stage stage
                       :state (persistent! state)
                       :message message}))))
  (fn [{:keys [results]}]
    {:result (if (empty? results) :fail :pass)
     :analysis results
     :sub-test :deepstall-test
     :reason (if (empty? results)
               "No results found in the log file"
               "")})
  {:stage :normal-flight})

(def-log-test nan-test
  (fn [state message]
      (if (empty? (filter (fn [[k v]] (and (float? v) (Float/isNaN v) (not= k :RelOriginAlt))) message))
        state
        (conj! state message)))
  (fn [state]
    (let [state (persistent! state)]
      (when-not (empty? state)
        {:result :fail
         :sub-test :nan-test
         :reason "Found log messages containing NaN's"
         :messages state})))
  [])

(def-log-test performance-test
  (fn [state {:keys [message-type] :as message}]
      (case message-type
        :PM (let [{:keys [LogDrop MaxT NLon NLoop]} message
                  {:keys [dropped long-loops maximum-loop-time pm-count timing-misses total-loops]} state]
              (assoc! state
                     :dropped (+ dropped LogDrop)
                     :long-loops (+ long-loops NLon)
                     :maximum-loop-time (max MaxT maximum-loop-time)
                     :timing-misses (if (and (pos? NLoop) (> (/ NLon NLoop) 0.06))
                                      (inc timing-misses)
                                      timing-misses)
                     :total-loops (+ total-loops NLoop)
                     :pm-count (inc pm-count)))
        :PARM (if (= (:Name message) "SCHED_LOOP_RATE")
                (assoc! state :loop-rate (:Value message))
                state)
        state))
  (fn [{:keys [dropped loop-rate long-loops maximum-loop-time pm-count timing-misses total-loops]}]
      (when (pos? pm-count) ; at least one PM message was found
        (conj []
              (when (pos? timing-misses)
                {:result :fail
                 :sub-test :performance-timing-misses
                 :reason (format "The autopilot missed it's timing requirement (>5% of the time in a measurement interval) %d times (out of %d intervals)" timing-misses pm-count)
                 :timing-misses timing-misses
                 :pm-count pm-count})
              (when (pos? dropped)
                {:result :fail
                 :sub-test :performance-log-drops
                 :reason (format "Dropped %d log messages" dropped)
                 :dropped dropped})
              (when (> maximum-loop-time (* 1.05 (/ 1e6 loop-rate)))
                {:result :fail
                 :sub-test :performance-loop-time
                 :reason (format "Longest message exceeded the target loop rate by more then 5%% (%d us)" maximum-loop-time)
                 :loop-rate loop-rate
                 :long-loops long-loops
                 :maximum-loop-time maximum-loop-time
                 :total-loops total-loops})
              )))
  {:dropped 0
   :loop-rate 50 ; nothing should run slower then this
   :long-loops 0
   :maximum-loop-time 0
   :pm-count 0
   :timing-misses 0
   :total-loops 0})

(def-log-test power-test
  (fn [state {:keys [message-type] :as message}]
    (if (= message-type :POWR)
      (let [vcc (:Vcc message)
            {:keys [vcc-min vcc-max]} state]
        (assoc! state
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
                     :vcc-max vcc-max})))))
  {:vcc-min Float/MAX_VALUE
   :vcc-max 0})

(defn analyze-log
  "Runs a selection of tests over a DF log."
  ([stream] (analyze-log stream [nan-test performance-test power-test]))
  ([stream tests]
   (remove empty?
           (flatten
             (mapv
               (fn [{:keys [state summarize-fn]}]
                   (summarize-fn state))
               (reduce (fn [tests message]
                           (mapv (fn [{:keys [state test-fn] :as test}]
                                     (assoc test :state (test-fn state message))) tests))
                       (mapv #(assoc % :state (transient (:initial-state %))) tests)
                       (parse-bin stream)))))))

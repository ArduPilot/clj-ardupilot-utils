(defproject ardupilot/clj-ardupilot-utils "0.1.2-SNAPSHOT"
  :description "A collection of utilities for use with ArduPilot or similair autopilots"
  :url "https://github.com/wickedshell/clj-ardupilot-utils"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-mavlink "0.1.2"]
                 [commons-io/commons-io "2.5"]]
  :plugins [[perforate "0.3.4"]
            [jonase/eastwood "0.3.5" :exclusions [org.clojure/clojure]]
            [lein-ancient "0.6.15"]
            [lein-kibit "0.1.7" :exclusions [org.clojure/clojure]]]
  :profiles {:dev {:resource-paths ["test/resources"]}}
  :global-vars {*warn-on-reflection* true})

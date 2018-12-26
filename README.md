# clj-ardupilot-utils

[![Build Status](https://semaphoreci.com/api/v1/ardupilot/clj-ardupilot-utils/branches/master/badge.svg)](https://semaphoreci.com/ardupilot/clj-ardupilot-utils)

A Clojure library of helper utilities for use with [Ardupilot](https://github.com/ArduPilot/ardupilot/).

## Installation

`clj-ardupilot-utils` is available from [Clojars](https://clojars.org/ardupilot/clj-ardupilot-utils):

```
[ardupilot/clj-ardupilot-utils "0.1.0"]
```

## Usage

### Parsing a DataFlash log

To parse an ArduPilot dataflash log, use `log-analyzer/parse-bin` which returns a lazy sequence of messages

```clojure
=> (require ['ardupilot-utils.log-reader :as 'log-reader]
            ['clojure.java.io :as ;io])
=> (log-reader/parse-bin (io/input-stream (io/file "my-log.BIN")))
```

### Analyze a DataFlash log

`clj-ardupilot-utils` provides a log analyzer based losely off the log analysis [tool](https://github.com/ArduPilot/ardupilot/tree/master/Tools/LogAnalyzer) within ArduPilot.

```clojure
=> (require ['ardupilot-utils.log-analysis :as 'log-analysis]
            ['clojure.java.io :as ;io])
=> (log-analysis/analyze-log (io/input-stream (io/file "my-log.BIN")))
()
```

`analyze-log` takes an input stream, which it will run all tests, or a provided subset of tests over. Any test that fails will be returned, otherwise an empty list is returned.

## License

Copyright © 2017 Michael du Breuil

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

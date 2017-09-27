(ns ardupilot-utils.impl.log-reader
    (:require [clojure.string :as string])
    (:import [java.io EOFException]))

(def ^:const LOG-HEADER-BYTE1 0xA3)
(def ^:const LOG-HEADER-BYTE2 0x95)

(def ^:const FORMAT-MESSAGE-ID 128)

(defmacro find-next-message
  "Finds the next available message"
  [reader]
  `(try
     (loop [stage# :header-1]
           (case stage#
             :header-1 (if (= (.readUnsignedByte ~reader) LOG-HEADER-BYTE1)
                         (recur :header-2)
                         (recur :header-1))
             :header-2 (if (= (.readUnsignedByte ~reader) LOG-HEADER-BYTE2)
                         (recur :header-id)
                         (recur :header-1))
             :header-id (.readUnsignedByte ~reader)))
     (catch EOFException e#)))

(defonce UINT64-MAX-VALUE (.toBigInteger 18446744073709551615N))

(defmacro read-field
  "Attempts to read the requested format type"
  [field-type reader]
  `(case ~field-type
     \a (short-array (repeatedly 32 #(.readShort ~reader)))                     ; int16[32]
     \b (.readByte ~reader)                                                     ; int8
     \B (.readUnsignedByte ~reader)                                             ; uint8
     \h (.readShort ~reader)                                                    ; int16
     \H (.readUnsignedShort ~reader)                                            ; uint16
     \i (.readInt ~reader)                                                      ; int32
     \I (bit-and (long (.readInt ~reader)) 0xFFFFFFFF)                          ; int32
     \f (.readFloat ~reader)                                                    ; float
     \d (.readDouble ~reader)                                                   ; double
     \n (apply str (repeatedly  4 #(let [read-char# (char (.readByte ~reader))] ; char[4]
                                     (when (not= read-char# \u0000) read-char#))))
     \N (apply str (repeatedly 16 #(let [read-char# (char (.readByte ~reader))] ; char[16]
                                     (when (not= read-char# \u0000) read-char#))))
     \Z (apply str (repeatedly 64 #(let [read-char# (char (.readByte ~reader))] ; char[64]
                                     (when (not= read-char# \u0000) read-char#))))
     \c (* (.readShort ~reader) 1e-2)                                           ; int16 * 100
     \C (* (.readUnsignedShort ~reader) 1e-2)                                   ; uint16 * 100
     \e (* (.readInt ~reader) 1e-2)                                             ; int32 * 100
     \E (* (bit-and (long (.readInt ~reader)) 0xFFFFFFFF) 1e-2)                 ; uint32 * 100
     \L (* (.readInt ~reader) 1e-7)                                             ; int32 * 1e7
     \M (.readUnsignedByte ~reader)                                             ; uint8-flight-mode
     \q (.readLong ~reader)                                                     ; int64
     \Q (bigint (.and (BigInteger/valueOf (.readLong ~reader)) UINT64-MAX-VALUE)) ; uint64
     (throw (ex-info "Unknown field" {:field-type ~field-type}))))

(defmacro merge-format-message
  "Handles the intermediate format message representation"
  [format-messages message]
  `(let [names# (string/split (:Columns ~message) #",")
         format# (:Format ~message)
         message-name# (:Name ~message)]
     (assoc ~format-messages
            (:Type ~message) {:name message-name#
                              :fields (mapv #(hash-map :name (keyword (get names# %))
                                                       :type (get format# %))
                                            (range (count format#)))
                              :message-type (keyword message-name#)})))

(defmacro merge-format-message-by-type
  "Handles the intermediate format message representation"
  [format-messages message]
  `(let [names# (string/split (:Columns ~message) #",")
         format# (:Format ~message)
         message-name# (:Name ~message)]
     (assoc ~format-messages
            (keyword message-name#) {:name message-name#
                              :fields (mapv #(hash-map :name (keyword (get names# %))
                                                       :type (get format# %))
                                            (range (count format#)))
                              :message-type (keyword message-name#)})))

;;;; Summary: Reading the configuration files.
;;;; Author:  Arnout Roemers
;;;;
;;;; This namespace contains the functions to read the configuration text that
;;;; specifies options, e.g. which .gluer files to use.

(ns gluer.config
  (:use [clojure.string :only (split split-lines trim)]))

;;; The configuration handling functions.

(defmulti handle
  "This function handles a key-value pair found in a configuration text. The
  dispatch value is on the `key' parameter."
  (fn [key value config] key))

(defmethod handle :default
  [key value config]
  (throw (IllegalArgumentException. (format "Configuration key '%s' is unknown." key))))

(defmethod handle "glue"
  [key value config]
  (update-in config [:glue] conj value))

(defmethod handle "verbose"
  [key value config]
  (assoc config :verbose (= value "true")))


;;; Reading the configuration.

(defn read-config
  "Reads the supplied configuration text and returns a map of options. The format
  of the configuration is as follows: if a line contains a colon, the text before
  the colon is the key and the text after the colon is the value. Other lines are
  ignored. Currently the following keys are supported:

   - glue:    A .gluer file to use, can be specified multiple times. The :glue 
              key in the retured map contains a set of filenames.
   - verbose: Value is either true or false, indicating whether verbose logging 
              should be displayed.

  Example configuration text:

    This is just comment text, since it does not contain a colon.
    Empty lines are ignored as well.

    glue: relative/path/to/file.gluer
    glue: paths/are/relative/from/the/config.file
    glue: more/colons:are:part/of/the.value

    verbose: true"
  [text]
  (loop [lines (split-lines text)
         config {}]
    (if-let [line (first lines)]
      (let [splitted (split line #"\s*:\s*" 2)]
        (if (< 1 (count splitted))
          (let [[key value] splitted]
            (recur (rest lines) (handle (trim key) value config)))
          (recur (rest lines) config)))
      config)))

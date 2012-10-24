;;;; Summary: The main entry point of the tool when used as a checker.
;;;; Author:  Arnout Roemers
;;;;
;;;; The functions in this namespace connects the various checking functions
;;;; available in the `gluer.logic' namespace, as to report validation errors 
;;;; and/or warnings to the user.

(ns gluer.core
  (:require [gluer.logic :as l]
            [gluer.resources :as r]
            [gluer.config :as c])
  (:use     [gluer.logging]
            [clojure.tools.cli :only (cli)])
  (:gen-class))

;;; The command-line definitions, such as the help text and the options.

(def help-text-format
  "The format string for the help string is formed in this definition.
  The resulting format string currently requires one parameter: the
  switches that can be used at the command-line."
  (apply str (interpose "\n" (list
    "Welcome to the Gluer checker. This tools checks the specified gluer files"
    "for consistency. Use as follows:"
    ""
    " java -cp <class-path> -jar gluer-VERSION-standalone.jar [switches] [gluer-files]"
    ""
    "The <class-path> should at least contain the components you want to glue"
    "and optionally plug-ins. The following switches can be used:"
    "%1$s" ; the command-line switches
    "To use the tool as a framework for your application, start your application"
    "as follows:"
    ""
    " java -cp <class-path>:"
    "      -javaagent:gluer-VERSION-standalone.jar=<gluer-file>[:<other-gluer-files>]"
    "      <your.app.Main>"))))
          

(defn- display-help-text
  "Prints the help text (as defined by help-text-format), including the supplied
  command-line options text."
  [cli-banner]
  (println (format help-text-format (clojure.string/replace cli-banner "Usage:\n" ""))))
  
(def commandline-opts
  [["-h" "--help" "Print this help." :flag true]
   ["-v" "--verbose" "Be verbose in output." :flag true]])

;;; The functions that direct the checking.

(defn- print-issues
  "Display the issue, formatted according to the issue's type. Current supported
  types are :warning and :error."
  [issues type]
  (let [prefix ({:warning "Warning" :error "Error"} type)]
    (doseq [issue issues]
      (println (format "%s: %s" prefix issue)))))

(defn- do-check ;--- This do-check approach could also be solved using monads.
  "This function can be wrapped around another function. If the returned value
  of that function is a map, it checks whether it contains a :warnings key 
  and/or an :errors key. In both cases, the corresponding values are displayed.
  The values can be collections. In case an :errors key is found and it is not
  empty, it also throws an InterruptedException."
  [value]
  (let [warnings (:warnings value)
        errors (:errors value)]
    (when (not (empty? warnings))
      (print-issues (if (seq? warnings) warnings (list warnings)) :warning))
    (when (not (empty? errors))
      (print-issues (if (seq? errors) errors (list errors)) :error)
      (throw (InterruptedException.))))
  value)

(defn- check
  "The main check function that directs the checking process."
  [gluer-file-names]
  (try
    (let [_   (log-verbose "Looking up Adapters...")
          adapter-library (r/build-adapter-library)
          _   (log-verbose "Adapter library:" adapter-library)
          _   (log-verbose "Checking adapter library data..." adapter-library)
          _ (do-check (l/check-adapter-library adapter-library))
          _   (log-verbose "Parsing .gluer files...")
          parsed-files (r/parse-gluer-files gluer-file-names)
          _   (log-verbose "Parsed .gluer files:" parsed-files)
          _   (log-verbose "Checking parsed .gluer files data...")
          _ (do-check (l/check-gluer-files parsed-files adapter-library))
          _   (log-verbose "Done checking.")]
      (println "No errors."))
    (catch InterruptedException ex 
      (println "Errors detected. Fix above errors and re-run the check."))))

;;; The main entry point.

(defn -main
  "The entry point of the tool when used as a checker."
  [& args]
  (let [[{:keys [verbose help]} args banner] (apply cli args commandline-opts)]
    (if help
      (display-help-text banner)
      (if-let [config-file-name (first args)]
        (let [config (c/read-config (slurp config-file-name))]
          (with-redefs [*verbose* (or verbose (:verbose config))]
            (check (:glue config))))
        (display-help-text banner)))))

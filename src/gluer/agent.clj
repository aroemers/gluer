(ns gluer.agent
  (:require [gluer.logic :as l]
            [gluer.resources :as r]
            [gluer.runtime :as runtime]
            [clojure.string :as s])
  (:use     [gluer.clauses]
            [gluer.logging])
  (:gen-class :name gluer.GluerAgent
              :main false
              :methods [^:static [premain [String java.lang.instrument.Instrumentation] void]]))

(defn- build-transformation-library
  "Based on an association library, builds a map with a fully qualified class
  name as key and a set of associations that transform that class as a value.
  For example: {\"test.NativeClient\" #{{:where ... :what ...} ...}}"
  [association-library]
  (apply merge-with (comp set concat)
    (for [association association-library]
      (let [transforms (transforms-classes association)]
        (zipmap transforms (repeat (hash-set association)))))))

(defn- generate-retrieval
  [association what-code]
  (let [where-type (type-of-where (:where association))]
    (if-let [using (:using association)]
      (format "gluer.Runtime.adapt(%1$s, Class.forName(\"%2$s\"), \"%3$s\")"
              what-code where-type (get-in using [:class :word]))
      (format "gluer.Runtime.adapt(%1$s, Class.forName(\"%2$s\"))"
              what-code where-type))))

(defn- inject-associations
  "Generates the code for the <what> clause per the specified associations and 
  injects this code based on the <where> clause in the javassist CtClass."
  [ctclass associations]
  (doseq [association associations]
    (log-verbose "Injecting association:" association)
    (let [what-code (generate-what association)
          retrieval-code (generate-retrieval association what-code)]
      (inject-where (:where association) ctclass retrieval-code))))

(defn- transform
  "Checks whether the specified class needs injection transformation(s), based
  on the supplied transformation library. If so, the new byte-code of the class
  is returned, nil otherwise."
  [class-name transformation-library]
  (when-let [associations (transformation-library class-name)]
    (log-verbose "Transforming class:" class-name)
    (let [ctclass (r/class-by-name class-name)]
      (inject-associations ctclass associations)
      (log-verbose "Done transforming class" 
        (str class-name ", will now be loaded in the JVM."))
      (.toBytecode ctclass))))

(defn transformer
  "This reifies a ClassFileTransformer for use in the JVM class loading
  instrumentation. The returned instance uses the transform function for its
  functionallity."
  [transformation-library]
  (reify java.lang.instrument.ClassFileTransformer
    (transform [this loader class-name class-being-redefined protection-domain classfile-buffer]
      (try
        (transform (s/replace class-name "/" ".") transformation-library)
        (catch Throwable t (println "[ERROR]" t) (System/exit 1))))))

(defn -premain
  [agent-args instrumentation]
  (with-redefs [*verbose* false]
    (log-verbose "Parsing .gluer files and searching for Adapters...")
    (let [gluer-files (s/split agent-args (re-pattern (java.io.File/pathSeparator)))]
      (when-let [association-library (l/check-gluer-files gluer-files)]
        (let [transformation-library (build-transformation-library association-library)
              adapter-library (r/build-adapter-library)
              transformer (transformer transformation-library)]
          (log-verbose "Transformation library:" transformation-library)
          (log-verbose "Adapter library:" adapter-library)
          (runtime/initialise adapter-library)
          (.addTransformer instrumentation transformer))))))

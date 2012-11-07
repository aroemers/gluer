;;;; Summary: Retrieving adapters, classes, class properties and parsed gluer files.
;;;; Author:  Arnout Roemers
;;;;
;;;; The functions in this namespace are for retrieving class models, based on
;;;; the the Javassist library, and other properties of classes, such as its
;;;; supertypes. 
;;;;
;;;; Another responsibility of this namespace is to retrieve the
;;;; available Adapters (within the class-path) and build a so-called 
;;;; Adapter-library.
;;;;
;;;; Furthermore, functions are available to get the parse parse result of 
;;;; .gluer files.

(ns gluer.resources
  (:refer-clojure :exclude [isa?])
  (:require [gluer.parser :as p])
  (:use     [clojure.string :only (split)]
            [clojure.set :only (difference union)])
	(:import  [javassist ClassPool Modifier]
            [java.io File]
            [org.scannotation AnnotationDB]))

;;; Inspecting which Adapter classes are available, without loading them.

(defn- class-path-to-urls
  "Returns a seqeunce of URLs of the (existing) supplied classpath entries."
  [class-path]
  (let [path-segments (split class-path (re-pattern File/pathSeparator))]
    (->> path-segments
         (map #(File. %))
         (filter #(.exists %))
         (map #(.toURL %)))))

(defn- adapter-class-names
  "Scans all classes in the classpath, without loading them, and returns
  a set of fully qualified names of those annotated with the gluer.Adapter
  annotation."
  []
  (let [urls (class-path-to-urls (System/getProperty "java.class.path"))
        annotation-db (doto (AnnotationDB.) (.scanArchives (into-array urls)))]
    (set (get (.getAnnotationIndex annotation-db) "gluer.Adapter"))))

;;; Retrieval of class models and properties of them.

(defn class-by-name
  "Returns a CtClass (from the javassist library) object representing the
  class, or nil if the class is not found in the current classpath."
  [class-name]
  (try
    (.get (ClassPool/getDefault) class-name)
    (catch Exception e nil)))

(defn- modifiers
  "Returns the modifiers of the specified class, encoded in an integer."
  [ctclass]
  (.getModifiers ctclass))

(defn public? ;--- Make a stand-alone Clojure javassist wrapper altogether?
  "Returns true if the supplied class is declared public, false otherwise."
  [ctclass]
  (Modifier/isPublic (modifiers ctclass)))

(defn inner?
  "Returns true if the supplied class is declared within another class, 
  false otherwise."
  [ctclass]
  (not (nil? (.getDeclaringClass ctclass))))

(defn static?
  "Returns true if the supplied class is a static inner class, false otherwise."
  [ctclass]
  (Modifier/isStatic (modifiers ctclass)))

(defn abstract?
  "Returns true if the supplied class is an abstract class, false otherwise."
  [ctclass]
  (Modifier/isAbstract (modifiers ctclass)))

(def supertypes-of 
  "A function that returns a set of the names of the direct supertypes 
  (classes and interfaces) of the supplied class. The function is memoized and 
  takes one parameter: the class name.

  Note that this function is memoized. Changes in the hierarchy after being
  requested by this function are not shown."
  (memoize (fn [class-name]
    (when-not (= class-name "java.lang.Object")
      (let [ctclass (class-by-name class-name)]
        (set (conj (map #(.getName %) (.getInterfaces ctclass))
                   (.getName (.getSuperclass ctclass)))))))))

(defn- remove-duplicates ;--- TODO: Move this to a utility namespace?
  "Given a sequence of sets, only the difference of a set and the union of all
  the former sets remain. Empty sets are removed. For example:

  (= (remove-duplicates [#{1 2 3} #{2 3 4} #{1 2 3 4}]) [#{1 2 3} #{4}])"
  [s]
  (filter seq (reduce (fn [cur nxt] (conj cur (apply difference nxt cur))) [] s)))

(defn leveled-supertypes-of
  "Returns a sequence of sets, where the first set contains the names
  of the direct supertypes of the supplied class, the second contains the 
  all supertypes of the former, and so on. For example:

  (leveled-supertypes-of \"java.util.List\")
  => (#{\"java.lang.Object\" \"java.util.Collection\"} #{\"java.lang.Iterable\"})"
  [class-name]
  (loop [types (supertypes-of class-name)
         result []]
    (if (seq types)
      (recur (set (mapcat supertypes-of types)) (conj result types))
      (remove-duplicates result))))

(defn isa?
  "Returns truthy if 'this' class-name is exactly 'that' class-name or a subtype, 
  false otherwise."
  [this that]
  (or (= this that) ((apply union (leveled-supertypes-of this)) that)))


;;; Building the adapter library and precedence relations.

(defn build-adapter-library
  "Based on a collection of fully qualified class names, this functions returns
  a library in the form of:

  {\"adapter.Name\" {:adapts-to (#{\"adapts.DirectlyToThis\"}
                               #{\"adapts.AlsoToThis\"})
                   :adapts-from #{\"adapts.from.This\", ..}}
   \"other.Adapter\" {..}}

  The :adapts-to value is based on the superclass and interfaces the 
  adapter extends/implements. The value of :adapts-to is in the format as Given
  by the `leveled-supertypes-of' function.

  The :adapts-from value is based on the single-argument constructors of 
  the adapter.

  This function does not perform checks on the adapters, so the :adapts-to
  or the :adapts-from values may be empty."
  []
  (->> (for [adapter-class-name (adapter-class-names)]
          (let [adapter-class (class-by-name adapter-class-name)
                adapts-from (->> (.getConstructors adapter-class)
                                 (map #(.getParameterTypes %))
                                 (filter #(= 1 (count %)))
                                 (map #(.getName (first %)))
                                 set)
                adapts-to (leveled-supertypes-of adapter-class-name)]
            {adapter-class-name {:adapts-from adapts-from
                                 :adapts-to adapts-to}}))
       (apply merge)))

(defn build-precedence-relations
  "Given a collection of filename-precedence pairs (as given by the 
  `parsed-precedences' function), return a map where the keys are those
  adapter class-names that are preceded by other adapters. The value of such a 
  key is a set with class-names that have precedence over that particular 
  adapter. For example:

  {\"preceded.Adapter\" #{\"by.some.preferred.Adapter\" \"and.ByThis\"}
   ...}"
  [file-precedences]
  (loop [precedences (map second file-precedences)
         accum {}]
    (if-let [precedence (first precedences)]
      (let [higher (get-in precedence [:higher :class :word])
            lower (get-in precedence [:lower :class :word])]
        (recur (rest precedences) (update-in accum [lower] #(set (conj % higher)))))
      accum)))


;;; Parsing the .gluer files and building the association library

;; The following definitions are (partial) regular expression, each yielding one
;; capture group.
(def ^:private package-pattern "(?:((?:\\w+\\.)*\\w+)\\.)")
(def ^:private class-pattern "((?:\\w|\\$)+)")
(def ^:private member-pattern "(\\w+)")

(def rules
  "The parse rules for .gluer files."
  {;; Root rule
   :root [:toplevel*]
   :toplevel #{ :precedence :association }

   ;; Precedence rules
   :precedence ["declare" "precedence" :higher "over" :lower]
   :higher [:class]
   :lower [:class]

   ;; Association rules
   :association ["associate" :where "with" :what :using?]
   :where #{ :where-clause-field }
   :what #{ :what-clause-new :what-clause-call :what-clause-single}
   :using ["using" :class]
   :class (re-pattern (str package-pattern "?" class-pattern))
   
   ;; Where clauses
   :where-clause-field ["field" :field]
   :field (re-pattern (str package-pattern "?" class-pattern "\\." member-pattern))

   ;; What clauses
   :what-clause-new ["new" :class]

   :what-clause-call ["call" :method]
   :method (re-pattern (str package-pattern "?" class-pattern "\\." member-pattern "\\(.*\\)"))

   :what-clause-single ["single" :class]})

(defn parse-gluer-files
  "Given the names of the .gluer files, this functions tries to parse all of
  them. The result is a sequence of parse results, structured as follows:

  ({:file-name \"spec1.gluer\"
    :parsed {:succes {...}}}
   {:file-name \"spec2.gluer\"
    :parsed {:error \"...\"}})

  Look at the `gluer.parser' namespace to learn more about the parse results."
  [gluer-file-names]
  (for [file-name gluer-file-names]
    {:file-name file-name
     :parsed (try 
                (p/parse rules :root (slurp file-name))
                (catch Exception ex {:error (str "Error parsing file: " ex)}))}))

(defn- toplevel-items
  "Given the parse result as given by the `parse-gluer-files' function, returns
  a set containing the toplevel items as identified by key `k'. Each item in the
  set is a filename-item pair. So, the return value has the following form: 

  #{ [\"filename\" value-of-k] 
     [\"filename\" value-of-k]
     [\"otherfile\" value-of-k]
     ... }

  Unsuccesfully parsed files are ignored."
  [parse-result k]
  (set (for [{:keys [file-name parsed]} parse-result
             :let [toplevel (get-in parsed [:succes :root :toplevel])
                   filtered (remove nil? (map k toplevel))]
             item filtered]
         [file-name item])))

(defn parsed-associations
  "Given the parse result as given by the `parse-gluer-files' function, returns
  a set containing filename-association pairs. The return value has the 
  following form: 

  #{ [\"filename\" {:where {...} :what {...} ... }] 
     [\"filename\" {:where {...} :what {...} ... }] 
     [\"otherfile\" {:where {...} :what {...} ... }] 
     ... }

  Unsuccesfully parsed files are ignored."
  [parse-result]
  (toplevel-items parse-result :association))

(defn parsed-precedences
  "Given the parse result as given by the `parse-gluer-files' function, returns
  a set containing filename-precedence pairs. The return value has the 
  following form: 

  #{ [\"filename\" {:higher {...} :lower {...} }] 
     [\"filename\" {:higher {...} :lower {...} }]
     [\"otherfile\" {:higher {...} :lower {...} }]
     ... }

  Unsuccesfully parsed files are ignored."
  [parse-result]
  (toplevel-items parse-result :precedence))

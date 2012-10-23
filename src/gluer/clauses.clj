;;;; Summary: The multi-methods for the <where> and <what> clauses.
;;;; Author:  Arnout Roemers
;;;;
;;;; This namespace contains the various multi-methods that need to be
;;;; implemented by the various types of <where> and <what> clauses. The tool,
;;;; both the checker as the runtime, depend on these implementations. Using
;;;; multi-methods here makes it easy to extend the framework with new types
;;;; of clauses.
;;;;
;;;; The multi-methods can be seperated in three categories: one for checking
;;;; a clause, one for determining the type of the clause and one for generating
;;;; or injecting code.
;;;;
;;;; The multi-method selection is based on the node-types in the parsing 
;;;; result. 

(ns gluer.clauses
  (:require [gluer.resources :as r])
  (:use     [gluer.logging])
  (:import  [java.io ByteArrayInputStream]
            [javassist CtField CtNewMethod]))

;;; The multi-methods to implement.

(defmulti check-where
  "Based on an association, the <where> clause is checked. Returns an error
  message if problems are detected, nil otherwise."
  (fn [association] (ffirst (:where association))))

(defmulti check-what
  "Based on an association, the <what> clause is checked. Returns an error
  message if problems are detected, nil otherwise."
  (fn [association] (ffirst (:what association))))

(defmulti type-of-where
  "Given a <where> clause, returns the fully qualified name of the (base) type 
  of the clause."
  (fn [where-clause] (ffirst where-clause)))

(defmulti type-of-what
  "Given a <what> clause, returns the fully qualified name of the (base) type of
  the clause."
  (fn [what-clause] (ffirst what-clause)))

(defmulti transforms-classes
  "Given an association, returns a set of class names it needs to transform.
  This is based on the <where> clause, although the function has access to the
  whole association."
  (fn [association] (ffirst (:where association))))

(defmulti generate-what
  "Given an association, returns the source code expressing the <what> clause."
  (fn [association] (ffirst (:what association))))

(defmulti inject-where
  (fn [where-clause ctclass retrieval-code] 
    (ffirst where-clause)))


;;; The 'new' what clause.

(defmethod check-what :what-clause-new
  [association]
  (let [{:keys [word line-nr]} (get-in association [:what :what-clause-new :class])]
    (if-let [ctclass (r/class-by-name word)]
      (let [constructors (.getConstructors ctclass)]
        (when (and (< 0 (count .getParameterTypes constructors)) 
                   (empty? (filter #(= 0 (count (.getParameterTypes %))) constructors)))
          (format "Class %s in `new' clause requires a no-argument constructor.")))
      (format "Class %s in `new' clause not found. Please check the name or your classpath." word))))

(defmethod type-of-what :what-clause-new
  [what-clause]
  (get-in what-clause [:what-clause-new :class :word]))

(defmethod generate-what :what-clause-new
  [association]
  (let [result (str "new " (get-in association [:what :what-clause-new :class :word]) "()")]
    (log-verbose "Generated <what> code:" result)
    result))


;;; The 'call' what clause.

(defmethod check-what :what-clause-call
  [association])

(defmethod type-of-what :what-clause-call
  [what-clause]
  (let [what (get-in what-clause [:what-clause-call :method :word])
        matched (re-matches #"((\w+\.)+)(\w+)\(.*\)" what)
        class-name (apply str (butlast (second matched)))
        method-name (nth matched 3)
        ctclass (r/class-by-name class-name)
        method (first (filter #(= (.getName %) method-name) (.getMethods ctclass)))]
    (.getName (.getReturnType method)))) 

(defmethod generate-what :what-clause-call
  [association]
  (get-in association [:what :what-clause-call :method :word]))


;;; The 'single' what clause.

(defmethod check-what :what-clause-single
  [association])

(defmethod type-of-what :what-clause-single
  [what-clause]
  (get-in what-clause [:what-clause-single :class :word]))

(defmethod generate-what :what-clause-single
  [association]
  (format "gluer.Runtime.single(\"%s\")" 
          (get-in association [:what :what-clause-single :class :word])))


;;; The 'field' where clause.

(defmethod check-where :where-clause-field
  [association]
  (let [{:keys [word line-nr]} (get-in association [:where :where-clause-field :field])]
    (when-not (re-matches #"((?:\w+\.)+)(\w+)" word)
      (str "The 'field' clause in line " line-nr " is not a valid field name."))))

(defmethod type-of-where :where-clause-field
  [where-clause]
  (let [where (get-in where-clause [:where-clause-field :field :word])
        matched (re-matches #"((?:\w+\.)+)(\w+)" where)
        class-name (apply str (butlast (second matched)))
        field-name (nth matched 2)
        ctclass (r/class-by-name class-name)
        field (.getDeclaredField ctclass field-name)]
    (.getName (.getType field))))

(defmethod transforms-classes :where-clause-field
  [association]
  (let [where (get-in association [:where :where-clause-field :field :word])
        matched (re-matches #"((?:\w+\.)+)(\w+)" where)]
    #{(apply str (butlast (second matched)))}))

(defmethod inject-where :where-clause-field
  [where-clause ctclass retrieval-code]
  (let [constructors (.getDeclaredConstructors ctclass)
        where (get-in where-clause [:where-clause-field :field :word])
        field-name (nth (re-matches #"((?:\w+\.)+)(\w+)" where) 2)
        constructor-code (format "_inject_%1$s();" field-name)
        field-code (format "private boolean _%1$s_injected;" field-name)
        method-code (format 
          (str "\nprivate void _inject_%1$s() {\n"
               "  if (! this._%1$s_injected) {\n"
               "    System.out.println(\"<_inject_%1$s()> Initialising field: %1$s.\");\n"
               "    this.%1$s = %2$s;\n"
               "    this._%1$s_injected = true;\n"
               "  }\n"
               "}") field-name retrieval-code)]
    (log-verbose "Adding field to class:" field-code)
    (.addField ctclass (CtField/make field-code ctclass) "false")
    (log-verbose "Adding method to class:" method-code)
    (.addMethod ctclass (CtNewMethod/make method-code ctclass))
    (log-verbose "Adding statement to beginning of constructors:" constructor-code)
    (doseq [constructor constructors]
      (.insertBeforeBody constructor constructor-code))))


;;; In the future, a macro for something like the following might be cool:

; (defwhere :where-clause-field
;   :rules          {:where-clause-field ["field" :wfield/field]
;                    :wfield/field       #"(\w+.)+\w+"}
;   :check-fn       (fn [association] (...))
;   :type-fn        (fn [clause] (...))
;   :transforms-fn  (fn [clause] (...)
;   :inject-fn      (fn [clause ctclass what-code] (...)))

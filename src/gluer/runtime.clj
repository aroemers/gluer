(ns gluer.runtime
  (:require [gluer.core :as c]
            [gluer.resources :as r])
  (:use     [gluer.logging])
  (:gen-class :name gluer.Runtime
              :main false
              :methods [^:static [adapt [Object Class] Object]
                        ^:static [adapt [Object Class String] Object]
                        ^:static [single [String] Object]]))

;; State and initialisation.

(declare ^:private adapters)

(defn initialise
  "Since the Runtime object is stateful (it has to know the adapter library),
  this namespace needs to be initialised. Otherwise, the -adapt function would
  have to rebuild the adapter library each call."
  [adapter-library]
  (def adapters adapter-library))


;; Adapter and constructor selection and instantiation.

(defn- equal-warning
  "Returns the warning of multiple equally eligible constructors for an Adapter."
  [adapter-name from-name actual-name]
  (format (str "WARNING: Multiple constructors equally eligible in Adapter '%s' "
               "for object of type '$s'. Chose constructor with parameter type "
               "'%s' semi-randomly.")
          adapter-name from-name actual-name))

(defn- closest-constructor 
  "Returns the constructor of the supplied class-name that matches the type of
  from-name the best."
  [class-name from-name]
  (let [clazz (Class/forName class-name)
        constructors (filter #(= (count (.getParameterTypes %)) 1) (.getConstructors clazz))
        supertypes-lvld (cons #{from-name} (r/leveled-supertypes-of from-name))
        param-name (fn [constructor] (.getName (first (.getParameterTypes constructor))))]
    (loop [lvl 0]
      (let [closest (filter #((nth supertypes-lvld lvl) (param-name %)) 
                            constructors)]
        (if (seq closest)
          (do (when (> (count closest) 1)
                (log-verbose (equal-warning class-name from-name 
                                            (param-name (first closest)))))
              (log-verbose "Chose constructor with parameter type:" (param-name (first closest)))
              (first closest))
          (recur (inc lvl)))))))

(defn -adapt
  "Given a from-object and a to-class, this function returns an object 
  compatible with the specified to-class, adapting the specified from-object.
  This function uses the core/get-adapter-for to retrieve the most suitable
  adapter."
  ([from-object to-clazz]
   (log-verbose "Adaptation requested from" (.getName (class from-object)) "to" (.getName to-clazz))
   (let [from-name (.getName (class from-object))
         to-name (.getName to-clazz)
         {:keys [result error]} (c/get-adapter-for from-name to-name adapters)]
     (if error 
       (throw (RuntimeException. error))
       (let [constructor (closest-constructor result from-name)]
         (log-verbose "Adapter choice is:" result)
         (.newInstance constructor (into-array [from-object]))))))
  ([from-object to-clazz adapter-name]
   (log-verbose "Adaptation requested from" (.getName (class from-object)) "to" 
                (.getName to-clazz) "using" adapter-name)
   (let [from-name (.getName (class from-object))
         to-name (.getName to-clazz)
         eligible-names (->> (c/eligible-adapters from-name to-name adapters)
                             (map first)
                             set)]
     (log-verbose "Eligible Adapters are:" eligible-names)
     (if-not (eligible-names adapter-name) 
       (throw (RuntimeException. (str "Explicitly chosen Adapter " adapter-name
                                      " is not suitable for adaption from "
                                      from-name " to " to-name ".")))
       (let [constructor (closest-constructor adapter-name from-name)]
         (log-verbose "Adapter" adapter-name "found suitable.")
         (.newInstance constructor (into-array [from-object])))))))


;; Runtime support for the 'single' what clause.

(def instances (atom {}))

(defn -single
  "Given a class-name, returns a previously created instance (by this function)
  or creates a new instance. The class should have a zero-arguments constructor."
  [class-name]
  (when-not (@instances class-name)
    (locking instances
      (when-not (@instances class-name)
        (let [instance (.newInstance (Class/forName class-name))]
          (swap! instances assoc class-name instance)))))
  (@instances class-name))

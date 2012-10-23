;;;; Summary: Functions and macros for logging to the terminal.
;;;; Author:  Arnout Roemers

(ns gluer.logging
  (:refer-clojure :exclude [replace])
  (:use [clojure.pprint :only (pprint)]
        [clojure.string :only (split replace)]))

(def ^:dynamic *verbose* false)

(defn prefix-lines ;--- Move this to a utility namespace?
  "Returns a string where every line in the `text' will be prepended by the
  `prefix'."
  [text prefix]
  (apply str prefix (interpose (str "\n" prefix) (split text #"\n"))))

(defn log-verbose
  "Call this function to log the supplied objects to the terminal, which will
  only actualy log if the *verbose* is set to true. All lines will be prepended
  by the function name that called log-verbose. 

  If all the objects are Strings, it will behave as println (with the prefix 
  prepended). Otherwise, all objects will be pretty printed on a seperate line.
  This behaviour makes it easy to log simple statements, but also to log some
  data."
  [& objects]
  (when *verbose*
    (let [former-fn (fn [] (-> (Throwable.) .getStackTrace (nth 3) .getClassName
                               (replace "_" "-") (replace "$" "/")))
          prefix (str "[GLUER " (former-fn) "] ")
          message (with-out-str
                    (if (every? string? objects) 
                      (apply println objects) 
                      (dorun (map #(if (string? %) (println %) (pprint %)) objects))))]
      (println (prefix-lines message prefix)))))

(defn log 
  "A small helper function, that pretty prints value v, before returning it."
  [v] 
  (clojure.pprint/pprint v) v)

(defmacro with-log-redefs
  "A macro that redefines the supplied functions, for the scope of its body, by 
  wrapping them with the log function. This macro has an implicit do around its 
  body. This macro is useful for testing or debugging functions. For example, 
  this would print 5 to *out*:

  (deftest test-plus-one
    (with-log-redefs [inc]
      (is (= 5 (inc 4)))))"
  [fs & body]
  (let [bindings (into [] (mapcat (fn [f] 
                                    (let [sym (gensym (str f "_log_redef_"))]
                                      [sym f 
                                       f `(fn [& args#] (log (apply ~sym args#)))])) 
                                  fs))]
    `(let ~bindings ~@body)))

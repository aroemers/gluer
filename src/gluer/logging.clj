(ns gluer.logging
  (:refer-clojure :exclude [replace])
  (:use [clojure.pprint :only (pprint)]
        [clojure.string :only (split replace)]))

(def ^:dynamic *verbose* false)

(defn log-verbose
  [& objects]
  (when *verbose*
    (let [former-fn (fn [] (-> (Throwable.) .getStackTrace (nth 3) .getClassName
                               (replace "_" "-") (replace "$" "/")))
          prefix (str "[GLUER " (former-fn) "] ")
          message (with-out-str
                    (if (every? string? objects) 
                      (apply println objects) 
                      (dorun (map #(if (string? %) (println %) (pprint %)) objects))))]
      (println (apply str prefix (interpose (str "\n" prefix) (split message #"\n")))))))

(defn log 
  "A small helper function, that pretty prints value v, before returning it."
  [v] 
  (clojure.pprint/pprint v) v)

(defmacro with-log-redefs
  "A macro that redefines the supplied functions, for the scope of its body, by 
  wrapping them with the log function. This macro has an implicit do around its 
  body. This macro is useful for testing functions. For example, this would
  print 5 to *out*:

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

;;;; Summary: The logic for checking and using the adapter and gluer data.
;;;; Author:  Arnout Roemers
;;;; 
;;;; This namespace contains the functions for checking the Adapter library and
;;;; the Gluer specifications. It also contains the logic for resolving the most
;;;; suitable Adapter for an injection.

(ns gluer.logic
  (:require [gluer.parser :as p]
            [gluer.resources :as r])
  (:use     [clojure.set :only (union difference)]
            [gluer.clauses]
            [gluer.logging]))

;;; Issue messages.

(def not-eligible-error 
  "Adapter %s is not eligible for adapting %s to %s.")

(def no-adapter-found-error
  "No suitable Adapter found for adapting %s to %s.")

(def resolution-conflict-error
  "Resolution conflict for adapting %s to %s. Eligible adapters are %s")

(defn format-issue
  [message file-name line-nr]
  (format "%s:%s %s" file-name line-nr message))


;;; Checking functions.

(defn check-adapter-library
  [adapter-library])

(defn- check-using
  [using])

(defn- check-association
  "This function checks a single association. It checks the individual clauses
  and, if no issues were found, continues to check the whole association 
  regarding finding a suitable adapter. The functions returns a map in the
  following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [association file-name adapter-library]
  ;; Retrieve data and perform clause checks.
  (let [where (:where association)
        what (:what association)
        using (:using association)
        check-where-result (check-where association)
        check-what-result (check-what association)
        check-using-result (and using (check-using using))]
    (if (or check-where-result check-where-result check-using-result)
      ;; Some errors during clause checks, report them.
      {:errors (concat (when check-where-result 
                          [(format-issue check-where-result file-name (:line-nr where))])
                       (when check-what-result 
                          [(format-issue check-what-result file-name (:line-nr what))])
                       (when check-using-result 
                          [(format-issue check-using-result file-name (:line-nr using))]))}
      ;; No errors during clause checks, check resolution.
      (let [where-type (type-of-where where)
            what-type (type-of-what (:what association))]
        (if using
          ;; Using keyword specified, check if it is eligible.
          (let [eligible-names (->> (eligible-adapters what-type where-type adapter-library)
                                    (map first)
                                    set)]
            (when-not (eligible-names (:word using))
              {:errors (format-issue (format not-eligible-error (:word using) what-type where-type)
                                     file-name (:line-nr using))}))
          ;; No using keyword, try to find a suitable adapter.
          (let [{:keys [result warning error]} (get-adapter-for what-type where-type adapter-library)]
            {:errors (when error [(format-issue error file-name (:line-nr where))])
             :warnings (when warning [(format-issue warning file-name (:line-nr where))])}))))))

(defn- check-valid-file
  [valid-file adapter-library]
  (let [associations (get-in valid-file [:parsed :succes :associations :association])
        file-name (:file-name valid-file)]
    (reduce (partial merge-with concat) 
            (map #(check-association % file-name adapter-library) associations))))

(defn- check-valid-files
  [valid-files adapter-library]
  (reduce (partial merge-with concat) (map #(check-valid-file % adapter-library) valid-files)))


(defn check-gluer-files
  "Given the parse results of the gluer files (as returned by 
  `gluer.resources/parse-gluer-files'), this function checks for warnings and 
  errors in them. Parse errors are reported as well, and succesfully parsed
  files will be checked nonetheless."
  [parsed-gluer-files adapter-library]
  (let [{valid :succes failed :error} (group-by (comp ffirst :parsed) parsed-gluer-files)
        errors (map #(str (get-in % [:parsed :file-name]) ": " (get-in % [:parsed :error])) failed)
        valid-check-result (check-valid-files valid adapter-library)]
    (update-in valid-check-result [:errors] concat errors)))


;;; Adapter resolution functions.

(defn eligible-adapters
  [from-name to-name adapter-library]
  (let [from-types-lvld (cons #{from-name} (r/leveled-supertypes-of from-name))
        from-types-all (apply union from-types-lvld)]
    (->> adapter-library
      (filter #(not (= from-types-all 
                       (difference from-types-all 
                                   (:adapts-from (second %))))))
      (filter #((apply union (:adapts-to (second %))) to-name)))))

(defn- closest-adapters
  [from-name to-name eligible]
    (let [from-types-lvld (cons #{from-name} (r/leveled-supertypes-of from-name))
          closest-from (loop [depth 0]
                         (let [from-types-lvl (nth from-types-lvld depth)
                               result (filter #(not (= from-types-lvl 
                                                       (difference from-types-lvl
                                                                   (:adapts-from (second %))))) eligible)]
                           (if (empty? result) (recur (inc depth)) result)))]
      (if (= 1 (count closest-from))
        closest-from
        (loop [depth 0]
          (let [result (filter #((nth (:adapts-to (second %)) depth) to-name) closest-from)]
            (if (empty? result) (recur (inc depth)) result))))))

(defn get-adapter-for
  [from-name to-name adapter-library]
  (let [eligible (eligible-adapters from-name to-name adapter-library)]
    (cond 
      (empty? eligible) 
        {:error (format no-adapter-found-error from-name to-name)}
      (= 1 (count eligible)) 
        {:result (ffirst eligible)}
      :otherwise
        (let [closest (closest-adapters from-name to-name eligible)]
          (if (= 1 (count closest)) 
            {:result (ffirst closest)}
            {:error (format resolution-conflict-error from-name to-name 
                            (apply str (interpose ", " (map first closest))))})))))

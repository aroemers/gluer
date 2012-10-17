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

;;; Checking functions.

(defn check-adapter-library
  [adapter-library])

(defn- check-association
  [association file-name adapter-library]
  (let [check-where-result (check-where association)
        check-what-result (check-what association)]
    (if (and (empty? check-where-result) (empty? check-where-result))
      (let [where-type (type-of-where (:where association))
            what-type (type-of-what (:what association))
            using (:using association)]
        )
      )))

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
        {:error (str "No suitable Adapter found for " from-name " to " to-name ".")}
      (= 1 (count eligible)) 
        {:result (ffirst eligible)}
      :otherwise
        (let [closest (closest-adapters from-name to-name eligible)]
          (if (= 1 (count closest)) 
            {:result (ffirst closest)}
            {:error (apply str "Resolution conflict for " from-name " to " 
                           to-name ". Eligible adapters are "
                           (interpose ", " (map first closest)))})))))

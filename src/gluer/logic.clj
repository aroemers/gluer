;;;; Summary: The logic for checking and using the adapter and gluer data.
;;;; Author:  Arnout Roemers
;;;; 
;;;; This namespace contains the functions for checking the Adapter library and
;;;; the Gluer specifications. It also contains the logic for resolving the most
;;;; suitable Adapter for an injection. This namespace is used by both the
;;;; checking as well as the the agent/runtime.

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
  (str "Resolution conflict for adapting %s to %s.\n"
       "\tConflicting adapters are: %s.\n"
       "\tThe closest adapters are: %s.\n"
       "\tAdd a 'using' clause to the association, or declare precedence rules "
       "for the closest adapters."))

(def precedence-error
  (str "Resolution conflict due to cyclic precedence declarations, see warnings above.\n"
       "\tConflicting adapters are: %s."))

(def not-found-error
  "Adapter %s is not found. Make sure it is spelled correctly and is in the classpath.")

(def not-adapter-error
  "Class %s is not an Adapter. Make sure it is annotated as such.")

(def adapts-to-nothing-error
  "Adapter %s does not extend or implement anything.")

(def adapts-from-nothing-error
  "Adapter %s does not have any single-argument constructors.")

(def adapter-not-public
  "Adapter %s is not declared public.")

(def adapter-not-statically-accesible
  "Adapter %s is not statically accessible. Make sure it is a top-level class, or a static inner class.")

(def adapter-not-concrete
  "Adapter %s is not a concrete class (it is abstract or an interface).")

(defn format-issue
  [message file-name line-nr]
  (format "%s:%s %s" file-name line-nr message))


;;; Adapter resolution functions.

(defn eligible-adapters
  "Based on the from-name type and to-name type and the adapter library, this
  function returns the adapters that are applicable concerning these types. The
  return value is a filtered adapter library."
  [from-name to-name adapter-library]
  (let [from-types-lvld (cons #{from-name} (r/leveled-supertypes-of from-name))
        from-types-all (apply union from-types-lvld)]
    (->> adapter-library
      (filter #(not (= from-types-all 
                       (difference from-types-all 
                                   (:adapts-from (second %))))))
      (filter #((apply union (:adapts-to (second %))) to-name)))))

(defn- closest-adapters
  "Based on the from-name type and to-name type and the eligible adapters, this 
  function returns the adapter(s) that are closest. Closest here means adapter
  that have an `adapts-from' closest to the from-name, and have an `adapts-to' 
  closest to to-name. The returned value is a filtered eligible adapter library."
  [from-name to-name eligible]
    (let [from-types-lvld (cons #{from-name} (r/leveled-supertypes-of from-name))
          closest-from (loop [depth 0]
                         (let [from-types-lvl (nth from-types-lvld depth)
                               result (filter #(not (= from-types-lvl 
                                                       (difference from-types-lvl
                                                                   (:adapts-from (second %))))) 
                                              eligible)]
                           (if (empty? result) (recur (inc depth)) result)))]
      (if (= 1 (count closest-from))
        closest-from
        (loop [depth 0]
          (let [result (filter #((nth (:adapts-to (second %)) depth) to-name) closest-from)]
            (if (empty? result) (recur (inc depth)) result))))))

(defn get-adapter-for
  "Given a fully qualified type `from-name', and a fully qualified type name 
  `to-name', and an adapter library, an adapter name is returned that is eligible
  and closest to the supplied types. See `eligible-adapters' and `closest-adapters'
  for more info on this. The adapter name is return in a map with key :result.
  If no suitable adapter is found, or a resolution conflict occured, the map 
  contains an :error key.

  Note that this function expects the adapter-library to hold a :precedence key,
  which value should contain the map with precedence-relations as given by the
  `gluer.resources/build-precedence-relations' function."
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
            (let [precedence-relations (:precedence adapter-library)
	                closest-names (set (keys closest))
                  preferred (remove #(some closest-names (precedence-relations %)) closest-names)]
              (cond
                (empty? preferred)
                  {:error (format precedence-error (apply str (interpose ", " closest-names)))}
                (= 1 (count preferred))
                  {:result (first preferred)}
                :otherwise
                  {:error (format resolution-conflict-error from-name to-name 
                                  (apply str (interpose ", " preferred))
                                  (apply str (interpose ", " closest-names)))})))))))


;;; Checking utilities.

(defn- depth-first-search ;--- Move this to a utility namespace?
  "Performs a depth-first-search in the (possibly nested) collection 'item' and 
  returns the first item that is true for the supplied predicate 'pred'."
  [pred item]
  (if (pred item) 
    item
    (when (and (coll? item) (first item))
      (loop [coll item]
        (if-let [result (depth-first-search pred (first coll))]
          result
          (when (not (empty? coll)) 
            (recur (rest coll))))))))

(defn- line-nr
  "Retrieve the line number of the supplied node, based on a (possibly nested)
  terminal."
  [node]
  (:line-nr (depth-first-search :line-nr node)))


;;; Adapter checking functions.

(defn check-adapter-library ;--- TODO: Check for possible resolution conflicts.
  "This function checks the adapter library for consistency. A map is returned,
  with possibly a :warnings key and/or an :errors key. The values of those keys
  consist of (possibly empty) sequences."
  [adapter-library]
  (letfn [(check-adapter [result [name data]]
            (let [ctclass (r/class-by-name name)]
              (update-in result [:errors] conj
                (when (empty? (:adapts-to data)) (format adapts-to-nothing-error name))
                (when (empty? (:adapts-from data)) (format adapts-from-nothing-error name))
                (when-not (r/public? ctclass) (format adapter-not-public name))
                (when (r/abstract? ctclass) (format adapter-not-concrete name))
                (when (and (r/inner? ctclass) (not (r/static? ctclass))) 
                  (format adapter-not-statically-accesible name)))))]
    (-> (reduce check-adapter {} adapter-library)
        (update-in [:errors] #(remove nil? %)))))


;;; Gluer specification checking functions.

(defn- check-using
  "Checks whether the class name in 'using' is an Adapter (or exists at all).
  If all is fine, nil is returned, otherwise an error message is returned."
  [using adapter-library]
  (let [adapter-name (get-in using [:class :word])]
    (when-not (adapter-library adapter-name)
      (if (r/class-by-name adapter-name)
        (format not-adapter-error adapter-name)
        (format not-found-error adapter-name)))))

(defn- check-association
  "This function checks a single filename-association pair. It checks the 
  individual clauses and, if no issues were found, continues to check the whole 
  association regarding finding a suitable adapter. The function returns a map
  in the following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [[file-name association] adapter-library]
  ;; Retrieve data and perform clause checks.
  (let [where (:where association)
        what (:what association)
        using (:using association)
        check-where-result (check-where association)
        check-what-result (check-what association)
        check-using-result (and using (check-using using adapter-library))]
    (if (or check-where-result check-what-result check-using-result)
      ;; Some errors during clause checks, report them.
      {:errors (concat (when check-where-result 
                          [(format-issue check-where-result file-name (line-nr where))])
                       (when check-what-result 
                          [(format-issue check-what-result file-name (line-nr what))])
                       (when check-using-result 
                          [(format-issue check-using-result file-name (line-nr using))]))}
      ;; No errors during clause checks, check resolution.
      (let [where-type (type-of-where where)
            what-type (type-of-what (:what association))]
        (cond using ;; Using keyword specified, check if it is eligible.
              (let [adapter-name (get-in using [:class :word])
                    eligible-names (->> (eligible-adapters what-type where-type adapter-library)
                                        (map first)
                                        set)]
                (when-not (eligible-names adapter-name)
                  {:errors [(format-issue (format not-eligible-error adapter-name what-type where-type)
                                         file-name (line-nr using))]}))
              (r/isa? what-type where-type) ;; A direct injection is possible, nothing to report.
              nil
              :else ;; No using keyword and no direct injection, so try to find a suitable adapter.
              (let [{:keys [result warning error]} (get-adapter-for what-type where-type adapter-library)]
                {:errors (when error [(format-issue error file-name (line-nr where))])
                 :warnings (when warning [(format-issue warning file-name (line-nr where))])}))))))

(defn- check-association-overlaps
  "This functions checks all filename-association pairs for overlap conflicts.
  The function returns a map in the following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [file-associations]
  ;; Loop through the associations, and check overlap with all the other associations AFTER it.
  ;; This way, no two associations are checked twice.
  (loop [associations file-associations
         errors-accum []]
    (if-let [[this-file this-assoc] (first associations)]
      (let [errors (for [[that-file that-assoc] (rest associations)] 
                      (when-let [error-msg (check-overlap this-assoc that-assoc)]
                        (format "Overlap detected with associations in %s:%s and %s:%s: %s"
                                this-file (line-nr this-assoc) that-file (line-nr that-assoc)
                                error-msg)))]
        (recur (rest associations) (concat errors-accum errors)))
      {:errors (remove nil? errors-accum)})))

(defn check-associations
  "Given a collection of file-association pairs, as returned by
  `gluer.resources/parsed-associations', it will check each association. The 
  function returns a map in the following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [file-associations adapter-library]
  (let [association-check-results (map #(check-association % adapter-library) file-associations)
        association-overlap-results (check-association-overlaps file-associations)]
    ;; Merge the {:errors [..] :warnings [..]} maps.
    (reduce (partial merge-with concat) 
            (conj association-check-results association-overlap-results))))

(defn- check-precedence
  "This functions checks a single file-precedence pair, given an adapter
  library. The function returns a map in the following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [[file-name precedence] adapter-library]
  (let [higher-class-name (get-in precedence [:higher :class :word])
        lower-class-name (get-in precedence [:lower :class :word])]
    (->> (concat (when-not (adapter-library higher-class-name)
                    [(if (r/class-by-name higher-class-name)
                       (format not-adapter-error higher-class-name)
                       (format not-found-error higher-class-name))])
                 (when-not (adapter-library lower-class-name)
                    [(if (r/class-by-name lower-class-name)
                      (format not-adapter-error lower-class-name)
                        (format not-found-error lower-class-name))])
                 (when (= higher-class-name lower-class-name)
                    "An Adapter cannot precede itself."))
         (map #(format-issue % file-name (line-nr precedence)))
         (hash-map :errors))))

(defn check-precedences
  "Given a collection of file-precedence pairs, as returned by
  `gluer.resources/parsed-precedences', it will check each precedence 
  declaration. The function returns a map in the following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [file-precedences adapter-library]
  (let [precedence-check-results (map #(check-precedence % adapter-library) file-precedences)]
    ;; Merge the {:errors [..] :warnings [..]} maps.
    (reduce (partial merge-with concat) precedence-check-results)))

(defn- check-precedence-cycle ;--- It was easier to write this specific code, than to use/create a
                              ;    graph library. Maybe fix this when other graphs are needed.
  "Given a map with precedence relations, a starting key (name) and a set
  holding the current path (initially empty), the function recursively checks 
  whether it can detect a cycle. The function returns a pair, where the first 
  item is the list of keys it has visited, and the second item is a boolean
  indicating whether there was a cycle in visiting those keys.

  For instance, when called with the following arguments:

    precedence-relations: {\"A\" #{\"B\"}
                           \"B\" #{\"C\"}}
    name:                 \"A\"
    path:                 #{}

  It would return:

    [(\"A\" \"B\") false]

  If no cycle was found, the full tree has been visited. Otherwise, part of the
  tree may be unvisited. Note that the precedence-relations may consist of
  multiple trees."
  [precedence-relations name path]
  ;; Check if node (name) has 'edges' to other nodes.
  (if-let [precedes (precedence-relations name)]
    ;; Loop through edges.
    (loop [ps precedes
           visited []]
      (if-let [p (first ps)]
        ;; Check if the node on the other end of the edge has already been visited.
        (if (path p)
          ;; Already visisted, cycle detected. Stop here.
          [[name] true]
          ;; Not visited yet, recursively check it for cycles.
          (let [[visit cycle?] (check-precedence-cycle precedence-relations p (conj path name))]
            ;; Check if cycle was detected.
            (if cycle?
              ;; Cycles detected, stop here.
              [(conj (concat visit visited) name) true]
              ;; No cycle detected yet, continue to visit other edges.
              (recur (rest ps) (concat visit visited)))))
        ;; Done visisting edges, no cycle detected.
        [(conj visited name) false]))
    ;; No edges found, consider this node not visited and no cycle detected.
    [[] false]))

(defn check-precedence-relations
  "Given a map with precedence relations (as returned by 
  `gluer.resources/build-precedence-relations'), this function checks if there
  are cycles in these precedence relations. The function returns the detected
  cycle messages in a collection in a map under the :warnings key:

  {:warnings (\"Cycle detected ..\" \"Cycle detected ..\" ...)}"
  [precedence-relations]
  ;; Loop through all 'trees' of precedence relations. Start with the entire 'forest' and 0 warnings.
  (loop [prels precedence-relations
         warnings []]
    ;; Check if any tree is left unchecked.
    (if (empty? prels)
      ;; All trees checked, returns accumulated warnings.
      {:warnings warnings}
      ;; Some tree(s) unchecked, check the current graph for cycles, starting from some random node.
      (let [[visited cycle?] (check-precedence-cycle prels (first (keys prels)) #{})]
        ;; Remove visited nodes from graph, accumulate a warning if a cycle was detected and loop.
        (recur (apply dissoc prels visited)
               (if cycle? 
                 (conj warnings 
                       (str "Cycle detected in precedence relations for adapters: " 
                            (apply str (interpose ", " visited)) "."))
                 warnings))))))

(defn check-parse-results
  "Given the parse results by `gluer.resources/parse-gluer-files', this checks
  if some of the files failed to parse. The function returns a map in the 
  following form:

  {:warnings (\"Some warning\")
   :errors (\"Some error\" \"Another error\")}

  The map values may be empty, which means no warnings and/or errors."
  [parsed-gluer-files]
  (let [failed (filter (comp :error :parsed) parsed-gluer-files)]
    {:errors (map #(str (get-in % [:parsed :file-name]) ": " (get-in % [:parsed :error])) 
                  failed)}))

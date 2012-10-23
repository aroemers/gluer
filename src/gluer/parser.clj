;;;--- TODO: Make parser a stand-alone library.

(ns gluer.parser
  (:use [clojure.string :only (split split-lines join)]))

(defn- parse-string
  [s remainder]
  (let [token (first remainder)
        word (:word token)]
    (if token
      (if (= s word)
        {:succes nil
         :remainder (rest remainder)}
        {:error (str "Unexpected token '" word "' on line " (:line-nr token) 
                     ", expected '" s "'.")})
      {:error (str "Unexpected EOF, expected '" s "'.")})))

(defn- parse-regex-rule
  [re remainder]
  (let [token (first remainder)
        word (:word token)]
    (if token
      (if-let [match (re-matches re word)]
        {:succes token;(if (vector? match) (first match) match)
         :remainder (rest remainder)}
        {:error (str "Unexpected token '" word "' on line " (:line-nr token) 
                     ", expected a word matching the regex '" re "'.")})
      {:error (str "Unexpected EOF, expected a word matching the regex '" re "'.")})))

(declare parse-rule parse-set-rule)

(defn- parse-vector-rule
  ([rules rule remainder]
   (parse-vector-rule rules rule remainder {}))
  ([rules rule remainder accum]
   (if-let [part (first rule)]
     (cond
       (string? part)
         (let [result (parse-string part remainder)]
           (if (contains? result :succes)
             (parse-vector-rule rules (rest rule) (:remainder result) accum)
             result))
       (keyword? part)
         (let [result (parse-rule rules part remainder)]
           (if (contains? result :succes)
             (parse-vector-rule rules (rest rule) (:remainder result) (merge accum (:succes result)))
             result))
       (set? part)
         (let [result (parse-set-rule rules part remainder)]
           (if (contains? result :succes)
             (parse-vector-rule rules (rest rule) (:remainder result) (merge accum (:succes result)))
             result)))
     {:succes accum
      :remainder remainder})))

(defn- parse-set-rule
  ([rules rule remainder]
   (parse-set-rule rules rule remainder []))
  ([rules rule remainder errors]
   (if-let [choice (first rule)]
     (let [result (cond 
                    (vector? choice) (parse-vector-rule rules choice remainder)
                    (string? choice) (parse-string choice remainder)
                    (keyword? choice) (parse-rule rules choice remainder)
                    (set? choice) (parse-set-rule rules choice remainder))]
       (if (contains? result :succes)
         result
         (parse-set-rule rules (rest rule) remainder (conj errors (:error result)))))
     {:error (apply str "Unexpected token sequence, with possible errors: "
                        (interpose " " errors))})))

(defn- parse-rule-more
  ([rules key remainder zero-allowed]
   (parse-rule-more rules key remainder zero-allowed []))
  ([rules key remainder zero-allowed accum]
   (let [result (parse-rule rules key remainder)]
     (if-let [succes (:succes result)]
       (parse-rule-more rules key (:remainder result) zero-allowed (conj accum succes))
       (if (or zero-allowed (seq accum))
         {:succes (mapcat vals accum)
          :remainder remainder}
         result)))))

(defn- parse-rule-optional
  [rules key remainder]
  (let [result (parse-rule rules key remainder)]
    (if-let [succes (:succes result)]
      {:succes (second (first succes))
       :remainder (:remainder result)}
      {:succes nil
       :remainder remainder})))
  
(defn regex? [v]
  (instance? java.util.regex.Pattern v))

(defn- parse-rule
  [rules key remainder]
  (if-let [suffix-key (#{\* \+ \?} (last (name key)))]
    (let [trimmed-key (keyword (join (drop-last (name key))))
          result (condp = suffix-key
                   \* (parse-rule-more rules trimmed-key remainder true)
                   \+ (parse-rule-more rules trimmed-key remainder false)
                   \? (parse-rule-optional rules trimmed-key remainder))]
      (if-let [succes (:succes result)]
        {:succes (hash-map trimmed-key succes)
         :remainder (:remainder result)}
        result))

    (let [rule (key rules)
          result (cond
                   (set? rule) (parse-set-rule rules rule remainder)
                   (vector? rule) (parse-vector-rule rules rule remainder)
                   (regex? rule) (parse-regex-rule rule remainder))]
      (if-let [succes (:succes result)]
        {:succes (hash-map key succes)
         :remainder (:remainder result)}
        result))))

(defn tokenize
  "Given a text and a separator (regular expression), returns a sequence of
  maps of the following structure: {:word \"foo\" :line-nr 1}. Empty lines are
  not tokenized."
  [text separator]
  (let [lines (split-lines text)]
    (flatten 
      (for [line-nr (range (count lines)) 
            :let [words (split (nth lines line-nr) separator)] 
            :when (seq (first words))] ; don't tokenize empty lines
        (map #(hash-map :word % :line-nr (inc line-nr)) words)))))

(defn parse
  "Parse the supplied text, based on the specified rules, starting at the
  specified start keyword. TODO: extend this documentation."
  [rules start text]
  (let [tokens (tokenize text #"\s+")
        result (parse-rule rules start tokens)
        {:keys [remainder succes error]} result]
    (cond
      error result
      (seq remainder) 
        {:error (str "Unparsed tokens from line " (:line-nr (first remainder)) 
                     ", likely due to errors in the source (or due to an " 
                     "error in the grammer).")
                       :parsed succes}
      :else result)))

;--- TODO: Compile time check if rules are valid: (check-rules rules)

;--- TODO: Replace set with some sequentual, since the order is now not specified? 
;          This means inconsistent parse trees for some grammars, or even parse errors. 
;          For now use sets only with sub-rules that have distinct "identifying" tokens 
;          in front.
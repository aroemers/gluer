(ns gluer.core-test
  (:use clojure.test
        gluer.core
        gluer.logging)
  (:require [gluer.resources :as r]))


; Class diagram: ^ means inheritance
;                : means adaptation (from B to A)
;
;       SuperSuperA
;           ^
;         SuperA ..        SuperB
;           ^      ``--..    ^
;      ,..- A ,,,,,,,,,,,;;; B < TwiceB
;     :     ^    ```--..     ^       :
;     :    SubA         ``` SubB     :
;     :                      ^       :
;      `:                 SubSubB    :
;        :                           :
;         `-.........2x.............-`

(def supertypes
  {"SuperB"      [#{"java.lang.Object"}]
   "B"           [#{"SuperB"} #{"java.lang.Object"}]
   "SubB"        [#{"B"} #{"SuperB"} #{"java.lang.Object"}]
   "TwiceB"      [#{"B"} #{"SuperB"} #{"java.lang.Object"}]
   "SubSubB"     [#{"SubB"} #{"B"} #{"SuperB"} #{"java.lang.Object"}]

   "SuperSuperA" [#{"java.lang.Object"}]
   "SuperA"      [#{"SuperSuperA"} #{"java.lang.Object"}]
   "A"           [#{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]
   "SubA"        [#{"A"} #{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]})

(def adapters
  {"BtoA"       {:adapts-from #{"B"}
                 :adapts-to [#{"A"} #{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]}
   "BtoSuperA"  {:adapts-from #{"B"}
                 :adapts-to [#{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]}
   "SubBtoA"    {:adapts-from #{"SubB"}
                 :adapts-to [#{"A"} #{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]}
   "TwiceBtoA1" {:adapts-from #{"TwiceB"}
                 :adapts-to [#{"A"} #{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]}
   "TwiceBtoA2" {:adapts-from #{"TwiceB"}
                 :adapts-to [#{"A"} #{"SuperA"} #{"SuperSuperA"} #{"java.lang.Object"}]}})


(deftest test-get-adapter-for
  (with-redefs [r/leveled-supertypes-of #(supertypes %)]
    (with-log-redefs []
      (testing "Testing valid results."
        (are [from to result] (= (:result (get-adapter-for from to adapters)) result)
          ; Exact match.
          "B"       "A"           "BtoA"
          ; Subtype of from.
          "SubSubB" "A"           "SubBtoA"
          ; Supertype of to.
          "B"       "SuperSuperA" "BtoSuperA"
          ; Subtype of from and supertype of to.
          "SubB"    "SuperA"      "SubBtoA"
          "SubSubB" "SuperSuperA" "SubBtoA"))
      (testing "Testing errors."
        (are [from to] (not (nil? (:error (get-adapter-for from to adapters))))
          ; No match. No adapter to a type (or a subtype of to).
          "SubB"    "SubA"
          ; No match. No adapter from a type (or a supertype of from).
          ; Might also be a warning, if adapters are found for subtypes of from.
          ; Or, this might be a reason for abstract Adapters.
          "SuperB"  "SuperA"
          ; Equal matches conflict.
          "TwiceB"  "A"))
      (testing "Testing warnings."
        #_(are [from to] (not (nil? (:warning (get-adapter-for from to adapters))))
          ; Possible runtime conflict error for subtype of from.
          "B"       "A")))))

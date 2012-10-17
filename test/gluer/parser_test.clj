(ns gluer.parser-test
  (:use clojure.test
        gluer.parser))

(deftest string-test
  (testing "Testing the string and sequence rule."
    (is (= (parse {:string-sequence ["parse-but-skip"]} :string-sequence "parse-but-skip")
           {:succes {:string-sequence {}} :remainder '()}))))

(deftest regex-test
  (testing "Testing the regular expression rule."
  	(is (= (parse {:regex #"\w+"} :regex "Hello")
  		     {:succes {:regex "Hello"} :remainder '()}))))

;; generalize and more tests.

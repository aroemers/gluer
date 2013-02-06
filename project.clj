(defproject gluer "0.1.0-SNAPSHOT"
  :description        "An Adapter-aware Dependency Injection framework."
  :url                "http://www.utwente.nl/ewi/trese/"
  :license            {:name "Eclipse Public License"
                       :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies       [[org.clojure/clojure "1.4.0"]
                       [org.clojure/tools.cli "0.2.2"]
                       [org.javassist/javassist "3.16.1-GA"]
                       [net.sf.scannotation/scannotation "1.0.2" :exclusions [javassist]]]
  :java-source-paths  ["src-java"]
  :main               gluer.core
  :aot                [gluer.agent gluer.runtime]
  :manifest           {"Premain-Class" "gluer.GluerAgent"})

;--- Update parser, to support spaces correctly (including implicit spaces)
;--- Update precedence rules syntax to comma-separated
;--- Update 'call' to 'retval'
;--- Add complete constructor calls for 'new' and 'single'
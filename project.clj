(defproject scruffian "1.0.0-SNAPSHOT"
  :description "Download service for iRODS."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.iplantc/clj-jargon "0.1.0-SNAPSHOT"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [slingshot "0.10.1"]
                 [compojure "1.0.1"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [clj-http "0.1.3"]]
  :aot [scruffian.core]
  :main scruffian.core)
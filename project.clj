(defproject scruffian/scruffian "1.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.iplantc/clj-jargon "0.1.3-SNAPSHOT"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [slingshot "0.10.1"]
                 [com.cemerick/url "0.0.6"]
                 [compojure "1.0.1"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [clj-http "0.1.3"]]
  :iplant-rpm {:summary "scruffian",
               :dependencies ["iplant-service-config >= 0.1.0-5"],
               :config-files ["log4j.properties"],
               :config-path "conf"}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/",
                 "renci.repository"
                 "http://ci-dev.renci.org/nexus/content/repositories/snapshots/"}
  :aot [scruffian.core]
  :main scruffian.core
  :min-lein-version "2.0.0"
  :plugins [[org.iplantc/lein-iplant-rpm "1.4.0-SNAPSHOT"]]
  :description "Download service for iRODS.")

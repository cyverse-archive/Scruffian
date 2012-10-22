(ns scruffian.config
  (:require [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as cc-props]
            [clj-jargon.jargon :as jargon]))

(def props (atom nil))

(def jg-config (atom nil))

(defn jargon-config [] @jg-config)

(def curl-path "/usr/local/bin/curl_wrapper.pl")

(defn jex-url [] (get @props "scruffian.app.jex"))

(defn listen-port
  []
  (Integer/parseInt (get @props "scruffian.app.listen-port")))

(defn irods-host [] (get @props "scruffian.irods.host"))
(defn irods-port [] (get @props "scruffian.irods.port"))
(defn irods-zone [] (get @props "scruffian.irods.zone"))
(defn irods-user [] (get @props "scruffian.irods.username"))
(defn irods-pass [] (get @props "scruffian.irods.password"))
(defn irods-home [] (get @props "scruffian.irods.home"))
(defn irods-resc [] (get @props "scruffian.irods.defaultResource"))
(defn irods-temp [] (get @props "scruffian.irods.temp-dir"))

(defn service-name [] (get @props "scruffian.app.service-name"))
(defn prov-url [] (get @props "scruffian.app.prov-url"))

(defn jargon-init
  []
  (jargon/init
   (irods-host)
   (irods-port)
   (irods-user)
   (irods-pass)
   (irods-home)
   (irods-zone)
   (irods-resc)))

(defn init
  []
  (def zkprops (cc-props/parse-properties "zkhosts.properties"))
  (def zkurl (get zkprops "zookeeper"))
  
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn
       "THIS APPLICATION CANNOT RUN ON THIS MACHINE.SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    
    (reset! props (cl/properties "scruffian"))
    (reset! jg-config (jargon-init))))
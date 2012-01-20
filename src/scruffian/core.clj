(ns scruffian.core
  (:gen-class)
  (:use [compojure.core]
        [ring.middleware.params]
        [ring.middleware.keyword-params]
        [ring.middleware.nested-params]
        [ring.middleware.multipart-params])
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.adapter.jetty :as jetty]
            [scruffian.controllers :as ctlr]
            [scruffian.actions :as actions]
            [scruffian.query-params :as qp]
            [scruffian.json-body :as jb]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as cc-props]
            [clojure-commons.file-utils :as ft]))

(def props (atom nil))

(defn listen-port 
  [] 
  (Integer/parseInt (get @props "scruffian.app.listen-port")))

(defroutes scruffian-routes  
  (GET "/download" request
       (ctlr/do-download request))
  
  (POST "/upload" request
        (ctlr/do-upload request))
  
  (POST "/urlupload" request
        (log/warn (str "Body: " (:body request)))
        (ctlr/do-urlupload request))
  
  (route/not-found "Not Found!"))


(defn site-handler
  [routes]
  (-> routes
    jb/parse-json-body
    (wrap-multipart-params {:store ctlr/store-irods}) 
    wrap-keyword-params
    wrap-nested-params
    qp/wrap-query-params))

(defn -main
  [& args]
  (def zkprops (cc-props/parse-properties "scruffian.properties"))
  (def zkurl (get zkprops "zookeeper"))
  
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    
    (reset! props (cl/properties "scruffian")))
  
  (actions/scruffian-init @props)
  (ctlr/init @props)
  (log/debug (str "properties: " @props))
  
  (log/warn (str "Listening on " (listen-port)))
  (jetty/run-jetty (site-handler scruffian-routes) {:port (listen-port)}))



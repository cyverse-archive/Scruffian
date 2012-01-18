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
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as cc-props]))

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
        (ctlr/do-urlupload request))
  
  (route/not-found "Not Found!"))

(defn store-irods
  [{filename :filename content-type :content-type stream :stream}]
  (partial ctlr/store stream filename))

(defn custom-multipart [handler]
  (wrap-multipart-params handler :store store-irods))

(defn site-handler
  [routes]
  (-> routes
    custom-multipart
    wrap-keyword-params
    wrap-nested-params))

(defn -main
  [& args]
  (def zkprops (cc-props/parse-properties "scruffian.properties"))
  (def zkurl (get zkprops "zookeeper"))
  
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY."))
    
    (reset! props (cl/properties "scruffian"))
    (System/exit 1))
  
  (actions/scruffian-init @props)
  
  (log/warn (str "Listening on " (listen-port)))
  (jetty/run-jetty (site-handler scruffian-routes) {:port (listen-port)}))



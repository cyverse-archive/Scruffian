(ns scruffian.core
  (:gen-class)
  (:use [compojure.core]
        [ring.middleware.params]
        [ring.middleware.keyword-params]
        [ring.middleware.nested-params]
        [ring.middleware.multipart-params]
        [scruffian.error-codes]
        [slingshot.slingshot :only [try+]]
        [clojure.data.json :only [json-str]])
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

(defn err-resp [action err-obj]
  {:status 500
   :body (-> err-obj
           (assoc :action action)
           (assoc :status "failure")
           json-str)})

(defroutes scruffian-routes  
  (GET "/download" request
       (try+
         (ctlr/do-download request)
         (catch error? err
           (log/error err)
           (err-resp "download" (:object &throw-context)) )
         (catch java.lang.Exception e
           (log/error e)
           (err-resp "download" (unchecked &throw-context)))))
  
  (POST "/upload" request
        (try+ 
          {:status 200 
           :body (-> (ctlr/do-upload request)
                   (assoc :action "upload")
                   json-str)} 
          (catch error? err
            (log/error err)
            (err-resp "upload" (:object &throw-context)))
          (catch java.lang.Exception e
            (log/error e)
            (err-resp "upload" (unchecked &throw-context)))))
  
  (POST "/urlupload" request
        (log/warn (str "Body: " (:body request)))
        (try+
          {:status 200 
           :body (-> (ctlr/do-urlupload request)
                   (assoc :action "url-upload")
                   json-str)}
          (catch error? err
            (log/error err)
            (err-resp "url-upload" (:object &throw-context)))
          (catch java.lang.Exception e
            (log/error e)
            (err-resp "url-upload" (unchecked &throw-context)))))
  
  (POST "/saveas" request
        (log/warn (str "Body: " (:body request)))
        (try+ 
          {:status 200
           :body (-> (ctlr/do-saveas request)
                   (assoc :action "saveas")
                   json-str)}
          (catch error? err
            (log/error err)
            (err-resp "saveas" (:object &throw-context)))
          (catch java.lang.Exception e
            (log/error e)
            (err-resp "saveas" (unchecked &throw-context)))))
  
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
  (def zkprops (cc-props/parse-properties "zkhosts.properties"))
  (def zkurl (get zkprops "zookeeper"))
  
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    
    (reset! props (cl/properties "scruffian")))
  
  (actions/scruffian-init @props)
  (ctlr/ctlr-init @props)
  (log/debug (str "properties: " @props))
  
  (log/warn (str "Listening on " (listen-port)))
  (jetty/run-jetty (site-handler scruffian-routes) {:port (listen-port)}))



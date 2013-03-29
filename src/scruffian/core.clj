(ns scruffian.core
  (:gen-class)
  (:use [compojure.core]
        [ring.middleware.params]
        [ring.middleware.keyword-params]
        [ring.middleware.nested-params]
        [ring.middleware.multipart-params]
        [scruffian.error-codes]
        [scruffian.config]
        [slingshot.slingshot :only [try+]])
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
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

(defn err-resp [action err-obj]
  {:status 500
   :body (-> err-obj
           (assoc :action action)
           (assoc :status "failure")
           cheshire/encode)})

(defroutes scruffian-routes
  (GET "/download" request
       (try+
         (ctlr/do-download request)
         (catch error? err
           (log/error err)
           (err-resp "file-download" (:object &throw-context)) )
         (catch java.lang.Exception e
           (log/error e)
           (err-resp "file-download" (unchecked &throw-context)))))

  (POST "/upload" request
        (try+
          {:status 200
           :body (-> (ctlr/do-upload request)
                   (assoc :action "file-upload")
                   cheshire/encode)}
          (catch error? err
            (log/error err)
            (err-resp "file-upload" (:object &throw-context)))
          (catch java.lang.Exception e
            (log/error e)
            (err-resp "file-upload" (unchecked &throw-context)))))

  (POST "/urlupload" request
        (log/warn (str "Body: " (:body request)))
        (try+
          {:status 200
           :body (-> (ctlr/do-urlupload request)
                   (assoc :action "url-upload")
                   cheshire/encode)}
          (catch error? err
            (log/error err)
            (err-resp "url-upload" (:object &throw-context)))
          (catch java.lang.Exception e
            (log/warn e)
            (err-resp "url-upload" (unchecked &throw-context)))))

  (POST "/saveas" request
        (log/warn (str "Body: " (:body request)))
        (try+
          {:status 200
           :body (-> (ctlr/do-saveas request)
                   (assoc :action "saveas")
                   cheshire/encode)}
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

(defn parse-args
  [args]
  (cli/cli
   args
    ["-c" "--config"
     "Set the local config file to read from. Bypasses Zookeeper"
     :default nil]
    ["-h" "--help"
     "Show help."
     :default false
     :flag true]))

(defn -main
  [& args]
  (let [[opts args help-str] (parse-args args)]
    (when (:help opts)
      (println help-str)
      (System/exit 0))

    (if (:config opts)
      (println (:config opts)))

    (if (:config opts)
      (local-init (:config opts))
      (init))

    (log/warn (str "Listening on " (listen-port)))

    (jetty/run-jetty (site-handler scruffian-routes) {:port (listen-port)})))

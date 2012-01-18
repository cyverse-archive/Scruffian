(ns scruffian.actions
  (:use [clj-jargon.jargon])
  (:require [clojure-commons.file-utils :as ft]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn scruffian-init
  [props]
  (let [host (get props "scruffian.irods.host")
        port (get props "scruffian.irods.port")
        zone (get props "scruffian.irods.zone")
        user (get props "scruffian.irods.username")
        pass (get props "scruffian.irods.password")
        home (get props "scruffian.irods.home")
        resc (get props "scruffian.irods.defaultResource")]
    (log/debug (str "Host: " host))
    (log/debug (str "Port: " port))
    (log/debug (str "Zone: " zone))
    (log/debug (str "User: " user))
    (log/debug (str "Pass: lol"))
    (log/debug (str "Home: " home))
    (log/debug (str "Resc: " resc))
    (init host port user pass home zone resc)))

(defn scruffy-copy
  [user istream dest-path]
  (let [ostream (output-stream dest-path)]
    (try
      (io/copy istream ostream)
      (finally
        (.close istream)
        (.close ostream)
        (set-owner dest-path user)))
    {:status "success"
     :id dest-path
     :permissions (dataobject-perm-map user dest-path)}))

(defn store
  ([istream user dest-path]
    (store istream user dest-path false))
  ([istream user dest-path threaded?]
    (with-jargon
      (cond
        (not (exists? (ft/dirname dest-path)))
        (do (log/warn (str "Directory " (ft/dirname dest-path) " does not exist."))
          {:status "failure" :id (ft/dirname dest-path)})
        
        (not (is-writeable? (ft/dirname dest-path)))
        (do (log/warn (str "Directory " (ft/dirname dest-path) " is not writeable.")) 
          {:status "failure" :id (ft/dirname dest-path)})
        
        :else
        (if threaded?
          (future
            (scruffy-copy user istream dest-path))
          (scruffy-copy user istream dest-path))))))

(defn download
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (log/debug "In download.")
  (with-jargon
    (cond
      (not (exists? file-path))
      {:status 404 :body (str "File " file-path " not found.")}
      
      (not (is-readable? user file-path))
      {:status 400 :body (str "File " file-path " is not readable.")}
      
      :else
      {:status 200
       :body (input-stream file-path)})))

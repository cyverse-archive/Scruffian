(ns scruffian.actions
  (:use [clj-jargon.jargon]
        [scruffian.error-codes])
  (:require [clojure-commons.file-utils :as ft]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [ring.util.response :as rsp-utils]
            [clojure.string :as string]))

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

(defn set-meta
  [path attr value unit]
  (with-jargon
    (set-metadata path attr value unit)))

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
  [istream user dest-path]
  (with-jargon
    (let [ddir (ft/dirname dest-path)]
      (when (not (exists? ddir))
        (mkdirs ddir))
      
      (cond          
        (not (is-writeable? user ddir))
        (log/error (str "Directory " ddir " is not writeable."))
        
        :else
        (do
          (scruffy-copy user istream dest-path)
          dest-path)))))

(defn- get-istream
  [user file-path]
  (with-jargon
    (cond
      (not (exists? file-path))
      {:status 404 :body (str "File " file-path " not found.")}
      
      (not (is-readable? user file-path))
      {:status 400 :body (str "File " file-path " is not readable.")}
      
      :else
      (input-stream file-path))))

(defn- new-filename
  [tmp-path]
  (string/join "." (drop-last (string/split (ft/basename tmp-path) #"\."))))

(defn upload
  [user tmp-path final-path]
  (with-jargon
    (cond
      (not (exists? final-path))
      {:status 400 :error_code ERR_DOES_NOT_EXIST :id final-path :action "upload"}
      
      (not (is-writeable? user final-path))
      {:status 400 :error_code ERR_NOT_WRITEABLE :id final-path :action "upload"}
      
      :else
      (let [new-fname (new-filename tmp-path)
            new-path  (ft/path-join final-path new-fname)]
        (if (exists? new-path)
          (delete new-path))
        (move tmp-path new-path)
        (set-owner new-path user)
        {:status 200
         :path new-path
         :action "upload"}))))

(defn download
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (log/debug "In download.")
  (let [istream (get-istream user file-path)]
    (if (map? istream)
      istream
      (-> {:status 200
           :body istream}
        (rsp-utils/header 
          "Content-Disposition" 
          (str "attachment; filename=\"" (ft/basename file-path) "\""))))))

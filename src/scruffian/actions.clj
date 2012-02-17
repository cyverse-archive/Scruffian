(ns scruffian.actions
  (:use [clj-jargon.jargon]
        [scruffian.error-codes])
  (:require [clojure-commons.file-utils :as ft]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.response :as rsp-utils]
            [clojure.string :as string]
            [clj-http.client :as client]))

(def curl-path (atom ""))
(def jex-url (atom ""))

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
    
    (reset! curl-path (get props "scruffian.app.curl-path"))
    (log/debug (str "curl path: " @curl-path))
    
    (reset! jex-url (get props "scruffian.app.jex"))
    (log/debug (str "jex url: " @jex-url))
    
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
  (let [ddir (ft/dirname dest-path)]
    (when (not (exists? ddir))
      (mkdirs ddir))
    
    (cond
      (not (is-writeable? user ddir))
      (log/error (str "Directory " ddir " is not writeable."))
      
      :else
      (do
        (scruffy-copy user istream dest-path)
        dest-path))))

(defn- get-istream
  [user file-path]
  (with-jargon
    (cond
      (not (exists? file-path))
      {:status "failure" 
       :action "download"
       :error_code ERR_DOES_NOT_EXIST}
      
      (not (is-readable? user file-path))
      {:status "failure"
       :action "download"
       :error_code ERR_NOT_READABLE}
      
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
      {:status "failure" 
       :error_code ERR_DOES_NOT_EXIST 
       :id final-path 
       :action "upload"}
      
      (not (is-writeable? user final-path))
      {:status "failure" 
       :error_code ERR_NOT_WRITEABLE 
       :id final-path 
       :action "upload"}
      
      :else
      (let [new-fname (new-filename tmp-path)
            new-path  (ft/path-join final-path new-fname)]
        (if (exists? new-path)
          (delete new-path))
        (move tmp-path new-path)
        (set-owner new-path user)
        {:status "success"
         :action "file-upload"
         :file {:id new-path
                :label (ft/basename new-path)
                :permissions (dataobject-perm-map user new-path)
                :date-created (created-date new-path)
                :date-modified (lastmod-date new-path)
                :file-size (str (file-size new-path))}}))))

(defn- jex-urlimport
  [user address filename dest-path]
  (let [curl-dir  (ft/dirname @curl-path)
        curl-name (ft/basename @curl-path)
        name-desc (str "URL Import of " filename " from " address)]
    (json/json-str 
      {:name name-desc
       :description name-desc
       :output_dir dest-path
       :create_output_subdir false
       :uuid (str (java.util.UUID/randomUUID))
       :username user
       :steps 
       [{:component 
         {:location curl-dir
          :name curl-name}
         :config
         {:params
          [{:name "-o"
            :value filename
            :order 1}
           {:name address
            :value ""
            :order 2}]
          :input []
          :output 
          [{:name "logs"
            :property "logs"
            :type "File"
            :multiplicity "collection"
            :retain false}]}}]})))

(defn- jex-send
  [body]
  (client/post 
    @jex-url 
    {:content-type :json 
     :body body}))

(defn urlimport
  "Pushes out an import job to the JEX.
   
   Parameters:
     user - string containing the username of the user that requested the import.
     address - string containing the URL of the file to be imported.
     filename - the filename of the file being imported.
     dest-path - irods path indicating the directory the file should go in."
  [user address filename dest-path]
  (let [req-body (jex-urlimport user address filename dest-path)
        {status :status body :body} (jex-send req-body)]
    (cond
      (= status 200)
      {:status "success" 
       :action "url-upload" 
       :msg "Upload scheduled."
       :url address
       :label filename
       :dest dest-path}
      
      (not= status 200)
      {:status "failure"
       :action "url-upload"
       :msg body
       :error_code ERR_REQUEST_FAILED})))

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

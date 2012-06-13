(ns scruffian.actions
  (:use [clj-jargon.jargon]
        [scruffian.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure-commons.file-utils :as ft]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.response :as rsp-utils]
            [clojure.string :as string]
            [clj-http.client :as client]))

(def curl-path "/usr/local/bin/curl_wrapper.pl")
(def jex-url (atom ""))
(def irods-username (atom ""))

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
    
    (reset! jex-url (get props "scruffian.app.jex"))
    (reset! irods-username (get props "scruffian.irods.username"))
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
    
    (when (not (is-writeable? user ddir))
      (log/error (str "Directory " ddir " is not writeable."))
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :path ddir} ))
    
    (scruffy-copy user istream dest-path)
    dest-path))

(defn- get-istream
  [user file-path]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    (when (not (exists? file-path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path file-path})) 
    (when (not (is-readable? user file-path))
      (throw+ {:error_code ERR_NOT_READABLE
               :user user
               :path file-path}))
    
    (if (= (file-size file-path) 0)
      ""
      (input-stream file-path))))

(defn- new-filename
  [tmp-path]
  (string/join "." (drop-last (string/split (ft/basename tmp-path) #"\."))))

(defn upload
  [user tmp-path final-path]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? final-path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST 
               :id final-path}))
    
    (when (not (is-writeable? user final-path))
      (throw+ {:error_code ERR_NOT_WRITEABLE 
               :id final-path}))
    
    (let [new-fname (new-filename tmp-path)
          new-path  (ft/path-join final-path new-fname)]
      (if (exists? new-path)
        (delete new-path))
      (move tmp-path new-path)
      (set-owner new-path user)
      (fix-owners new-path user @irods-username)
      {:status "success"
       :file {:id new-path
              :label (ft/basename new-path)
              :permissions (dataobject-perm-map user new-path)
              :date-created (created-date new-path)
              :date-modified (lastmod-date new-path)
              :file-size (str (file-size new-path))}})))

(defn- jex-urlimport
  [user address filename dest-path]
  (let [curl-dir  (ft/dirname curl-path)
        curl-name (ft/basename curl-path)
        job-name (str "url_import_" filename)
        job-desc (str "URL Import of " filename " from " address)]
    (json/json-str 
      {:name job-name
       :type "data"
       :description job-desc
       :output_dir dest-path
       :create_output_subdir false
       :uuid (str (java.util.UUID/randomUUID))
       :monitor_transfer_logs false
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
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (is-writeable? user dest-path))
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :user user
               :path dest-path}))
    
    (when (exists? (ft/path-join dest-path filename))
      (throw+ {:error_code ERR_EXISTS
               :path (ft/path-join dest-path filename)})))
  
  (let [req-body (jex-urlimport user address filename dest-path)
        {jex-status :status jex-body :body} (jex-send req-body)]
    (when (not= jex-status 200)
      (throw+ {:msg jex-body
               :error_code ERR_REQUEST_FAILED}))
    {:status "success" 
     :msg "Upload scheduled."
     :url address
     :label filename
     :dest dest-path}))

(defn download
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (log/debug "In download.")
  (let [istream (get-istream user file-path)]
    (-> {:status 200
         :body istream}
      (rsp-utils/header 
        "Content-Disposition" 
        (str "attachment; filename=\"" (ft/basename file-path) "\"")))))

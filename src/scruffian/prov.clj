(ns scruffian.prov
  (:use [slingshot.slingshot :only [try+]])
  (:require [clojure-commons.provenance :as p]
            [clojure-commons.file-utils :as f]
            [scruffian.config :as cfg]
            [clj-jargon.jargon :as jg]
            [clojure.tools.logging :as log]))

;;;Event Names
(defn download "download")
(defn upload "upload")
(defn url-import "url-import")

;;;Category Names

(def irods-file "irods-file")
(def irods-dir "irods-directory")
(def imported-url "imported-url")

;;;Utility functions
(defn url-protocol
  [url-str]
  (try
    (.getScheme (java.net.URI/create url-str))
    (catch Exception e
      nil)))

(defn url?
  [cm obj]
  (and (string? obj)
       (let [proto (url-protocol obj)]
         (or (= proto "http")
             (= proto "https")))))

(defn path?
  [cm obj]
  (and (string? obj)
       (or (jg/is-dir? cm obj)
           (jg/is-file? cm obj))))

(defn determine-category
  "Figures out the provenance category that is appropriate for the object
   passed in. 'cm' is a clj-jargon context map."
  [cm obj]
  (cond
   (url? cm obj)              imported-url
   
   (and (path? cm obj)
        (jg/is-dir? cm obj))  irods-dir
        
   (and (path? cm obj)
        (jg/is-file? cm obj)) irods-file
        
   :else                      nil))

(defn irods-domain-obj
  "Returns a domain object for obj.

   If obj is a string and directory in iRODS, then a Collection instance
   is returned.
"
  [cm obj]
  (cond
   (and (path? cm obj)
        (jg/is-dir? cm obj))
   (jg/collection cm obj)

   (and (path? cm obj)
        (jg/is-file? cm obj))
   (jg/data-object cm obj)
   
   :else obj))

(defn object-id
  [cm user obj]
  (let [domain-obj (irods-domain-obj cm obj)]
    (cond
     (url? cm obj)
     obj
     
     (path? cm obj)
     (str (.. domain-obj getCreatedAt getTime)
          "||"
          (cfg/irods-zone)
          "||"
          (.. domain-obj getAbsolutePath))
     
     :else
     "This is a string")))

(defn object-name
  [cm user obj]
  (let [domain-obj (irods-domain-obj cm obj)]
    (cond
     (url? cm obj)
     obj
     
     (path? cm obj)
     (f/basename (.getAbsolutePath domain-obj))

     :else
     "This is another string.")))

(defn arg-map
  [cm user obj-id event category &
   {:keys [proxy-user data]
    :or {proxy-user (cfg/irods-user)
         data       nil}}]
  (let [svc    (cfg/service-name)
        purl   (cfg/prov-url)]
    (p/prov-map purl obj-id user svc event category proxy-user data)))

(defn lookup
  [cm user obj]
  (try 
    (let [purl (cfg/prov-url)
          oid  (object-id cm user obj)]
      (if (p/exists? purl oid)
        (p/lookup purl oid)))
    (catch Exception e
      (log/warn e))
    (catch java.net.ConnectException ce 
      (log/warn ce))
    (catch Throwable t
      (log/warn t))))

(defn register
  [cm user obj & [parent-uuid desc]]
  (try 
    (let [obj-id (object-id cm user obj)
          obj-nm (object-name cm user obj)]
      (if-not (p/exists? (cfg/prov-url) obj-id)
        (p/register (cfg/prov-url) obj-id obj-nm desc parent-uuid))
      obj-id)
    (catch Exception e
      (log/warn e))
    (catch java.net.ConnectException ce 
      (log/warn ce))
    (catch Throwable t
      (log/warn t))))

(defn register-parent
  [cm user obj & [parent-uuid desc]]
  (try 
    (let [obj-id (object-id cm user obj)
          obj-nm (object-name cm user obj)]
      (if-not (p/exists? (cfg/prov-url) obj-id)
        (p/register (cfg/prov-url) obj-id obj-nm desc parent-uuid))
      (p/lookup (cfg/prov-url) obj-id))
    (catch Exception e
      (log/warn e))
    (catch java.net.ConnectException ce 
      (log/warn ce))
    (catch Throwable t
      (log/warn t))))

(defn send-provenance
  [cm user obj-id event category & {:keys [data]}]
  (try
    (log/warn
     (str "Log Provenance: "
          (p/log (arg-map cm user obj-id event category :data data))))
    (catch Exception e
      (log/warn e))
    (catch java.net.ConnectException ce 
      (log/warn ce))
    (catch Throwable t
      (log/warn t))))

(defn log-provenance
  [cm user obj event & {:keys [parent-uuid data]}]
  (let [obj-id  (register cm user obj parent-uuid)
        obj-cat (determine-category cm obj)]
    (log/warn (str "Object: " obj "\tID: " obj-id  "\tCategory: " obj-cat))
    (send-provenance cm user obj-id event obj-cat :data data)
    obj))
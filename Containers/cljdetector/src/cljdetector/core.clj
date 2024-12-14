(ns cljdetector.core
  (:require [clojure.string :as string]
            [cljdetector.process.source-processor :as source-processor]
            [cljdetector.process.expander :as expander]
            [cljdetector.storage.storage :as storage]))

(def DEFAULT-CHUNKSIZE 5)
(def source-dir (or (System/getenv "SOURCEDIR") "/tmp"))
(def source-type #".*\.java")

(defn ts-println [& args]
  (let [timestamp (.toString (java.time.LocalDateTime/now))
        message (string/join " " args)]
    (println timestamp args)
    (storage/add-update! {:timestamp timestamp :message message})))

(defn maybe-clear-db [args]
  (when (some #{"CLEAR"} (map string/upper-case args))
      (ts-println "Clearing database...")
      (storage/clear-db!)))

(defn maybe-read-files [args]
  (when-not (some #{"NOREAD"} (map string/upper-case args))
    (ts-println "Reading and Processing files...")
    (let [chunk-param (System/getenv "CHUNKSIZE")
          chunk-size (if chunk-param (Integer/parseInt chunk-param) DEFAULT-CHUNKSIZE)
          file-limit (if-let [limit (System/getenv "FILE_LIMIT")]
                      (Integer/parseInt limit)
                      Integer/MAX_VALUE)
          file-handles (source-processor/traverse-directory source-dir source-type)
          _ (ts-println "Processing" (min (count file-handles) file-limit) "files")
          chunks (source-processor/chunkify chunk-size file-handles)]
      (ts-println "Storing files...")
      (storage/store-files! file-handles)
      (ts-println "Storing chunks of size" chunk-size "...")
      (storage/store-chunks! chunks))))

(defn maybe-detect-clones [args]
  (when-not (some #{"NOCLONEID"} (map string/upper-case args))
    (ts-println "Identifying Clone Candidates...")
    (storage/identify-candidates!)
    (ts-println "Found" (storage/count-items "candidates") "candidates")
    (ts-println "Expanding Candidates...")
    (expander/expand-clones)))

(defn pretty-print [clones]
  (doseq [clone clones]
    (println "====================\n" "Clone with" (count (:instances clone)) "instances:")
    (doseq [inst (:instances clone)]
      (println "  -" (:fileName inst) "startLine:" (:startLine inst) "endLine:" (:endLine inst)))
    (println "\nContents:\n----------\n" (:contents clone) "\n----------")))

(defn maybe-list-clones [args]
  (when (some #{"LIST"} (map string/upper-case args))
    (ts-println "Consolidating and listing clones...")
    (pretty-print (storage/consolidate-clones-and-source))))



(defn -main
  "Starting Point for All-At-Once Clone Detection
  Arguments:
   - Clear clears the database
   - NoRead do not read the files again
   - NoCloneID do not detect clones
   - List print a list of all clones
  Environment Variables:
   - SOURCEDIR: directory containing source files
   - CHUNKSIZE: size of chunks to process
   - FILE_LIMIT: maximum number of files to process"
  [& args]
  (let [file-limit (or (System/getenv "FILE_LIMIT") "all")]
    (ts-println "Starting clone detection with file limit:" file-limit))
  (maybe-clear-db args)
  (maybe-read-files args)
  (maybe-detect-clones args)
  (maybe-list-clones args)
  (ts-println "Summary")
  (storage/print-statistics))

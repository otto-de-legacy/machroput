(ns de.otto.machroput.utils.http-utils
  (:require
    [org.httpkit.client :as http]
    [clojure.data.json :as json]))

(def POST http/post)
(def PUT http/put)
(def GET http/get)

(defn request-options [user password body]
  (let [opts {:body    body
              :headers {"Accept"       "application/json"
                        "Content-Type" "application/json"}}]
    (if (and user password)
      (assoc opts :basic-auth [user password])
      opts)))

(defn a-json-request [{:keys [url user password print-fn]} mthd path body]
  (let [target (str url path)]
    (try
      (let [{:keys [status body error]} @(mthd target (request-options user password body))]
        (if-not (nil? error)
          (throw (RuntimeException. error)))
        (if (< status 400)
          (if-not (empty? body)
            (json/read-str body :key-fn keyword))
          (throw (RuntimeException. (str "An error occured when requesting" target "status was:" status "body:" body)))))
      (catch Exception e
        (print-fn (str "An error occured when requesting " target (.getMessage e)))))))

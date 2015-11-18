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

(defn a-json-request [{:keys [url user password]} mthd path body]
  (let [target (str url path)]
    (let [{:keys [status body error]} @(mthd target (request-options user password body))]
      (when error
        (throw (RuntimeException. error)))
      (when (>= status 400)
        (throw (RuntimeException. (str "An error occured when requesting " target " status was:" status " body:" body))))
      (if-not (empty? body)
        (json/read-str body :key-fn keyword)))))

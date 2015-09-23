(ns de.otto.machroput.chronos.connection
  (:require
    [de.otto.machroput.utils.http-utils :refer :all]
    [clojure.data.json :as json]))

(defn has-parent-dependency? [json]
  (not (nil? (:parents json))))

(defprotocol ChronosApi
  (create-new-app [self json]))

(defrecord ChronosConnection [url user password print-fn]
  ChronosApi
  (create-new-app [self json]
    (let [json-str (json/write-str json)]
      (if (has-parent-dependency? json)
        (a-json-request self POST "/scheduler/dependency" json-str)
        (a-json-request self POST "/scheduler/iso8601" json-str)))))

(defn new-chronos-connection [{:keys [url user password]} print-fn]
  (map->ChronosConnection
    {:url      url
     :user     user
     :password password
     :print-fn print-fn}))

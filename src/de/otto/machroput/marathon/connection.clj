(ns de.otto.machroput.marathon.connection
  (:require
    [de.otto.machroput.utils.http-utils :refer :all]
    [clojure.data.json :as json]))

(defn is-deployment-for [deployment appid]
  (let [affectedApps (:affectedApps deployment)]
    (not (empty? (filter #(= % appid) affectedApps)))))

(defn current-app-deployments [deployments appid]
  (some->> deployments
           (filter #(is-deployment-for % appid))))

(defn is-app-currently-deploying? [current-deployments app-id]
  (not (empty? (current-app-deployments current-deployments app-id))))

(defn current-deployment-version-for [deployments appid]
  (some-> (current-app-deployments deployments appid)
          (first)
          (:version)))

(defprotocol MarathonAPI
  (start [self])
  (create-new-app [self json])
  (get-apps [self])
  (get-app [self app-id])
  (get-app-versions [self app-id])
  (get-app-config [self app-id version])
  (get-deployments [self])
  )

(defprotocol MaratohnAPIHelper
  (determine-deployment-version [self app-id])
  (deployment-still-running? [self depl-version])
  (deployment-exists-for? [self app-id]))

(defrecord MarathonConnection [url user password print-fn]
  MarathonAPI
  (create-new-app [self json]
    (let [app-id (:id json)
          json-str (json/write-str json)]
      (a-json-request self PUT (str "/v2/apps/" app-id) json-str)))

  (get-apps [self]
    (a-json-request self GET "/v2/apps" nil))

  (get-app [self app-id]
    (a-json-request self GET (str "/v2/apps/" app-id) nil))

  (get-app-versions [self app-id]
    (a-json-request self GET (str "/v2/apps/" app-id "/versions") nil))

  (get-app-config [self app-id version]
    (a-json-request self GET (str "/v2/apps/" app-id "/versions/" version) nil))

  (get-deployments [self]
    (a-json-request self GET "/v2/deployments" nil))

  MaratohnAPIHelper
  (determine-deployment-version [self appid]
    (let [max-retries 20]
      (loop [tries 0]
        (let [current-deployments (get-deployments self)
              result (current-deployment-version-for current-deployments appid)]
          (if-not (nil? result)
            result
            (if (< tries max-retries)
              (do
                (Thread/sleep 500)
                (recur (+ tries 1)))
              nil))))))

  (deployment-still-running? [self depl-version]
    (let [current-depl (get-deployments self)
          current-depl-versions (set (map :version current-depl))]
      (contains? current-depl-versions depl-version)))

  (deployment-exists-for? [self app-id]
    (let [current-deployments (get-deployments self)]
      (is-app-currently-deploying? current-deployments app-id))))

(defn new-marathon-connection [{:keys [url user password]}]
  (map->MarathonConnection
    {:url      url
     :user     user
     :password password}))

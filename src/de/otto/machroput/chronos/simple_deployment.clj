(ns de.otto.machroput.chronos.simple-deployment
  (:require
    [de.otto.machroput.deploy-api :refer :all]
    [de.otto.machroput.chronos.connection :as c]
    [clojure.data.json :as json]))

(defrecord SimpleChronosDeployment [conn print-fn]
  DeploymentAPI
  (start-deployment [self json version]
    (print-fn "Starting simple chronos deployment")
    (print-fn "Thats your Deploy-JSON for version " version ": ")
    (print-fn (json/write-str json))
    (c/create-new-app conn json)
    (print-fn "Deployment successful?!?!")))

(defn new-simple-chronos-deployment
  ([cconf]
   (new-simple-chronos-deployment cconf println))
  ([cconf print-fn]
   (map->SimpleChronosDeployment
     {:conn     (c/new-chronos-connection cconf print-fn)
      :print-fn print-fn})))

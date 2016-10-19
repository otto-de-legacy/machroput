(ns de.otto.machroput.marathon.deployment
  (:require
    [de.otto.machroput.marathon.connection :as mc]
    [de.otto.machroput.deploy-api :refer :all]
    [de.otto.machroput.marathon.checks :as checks]
    [clojure.data.json :as json]))

(defn get-current-version! [{:keys [print-fn app-version-fn]}]
  (try
    (app-version-fn)
    (catch Exception e
      (print-fn (str "An error occured when trying to execute the current-version-fn " (.getMessage e)))
      nil)))

(defn build-deployment-info [json version marathon-deploy-version]
  {:version                 version
   :id                      (:id json)
   :marathon-deploy-version marathon-deploy-version
   :instances               (:instances json)})

(defn all-deployment-checks-successful? [{:keys [mconn deploy-conf]} deploy-info]
  (->> (:post-deployment-checks deploy-conf)
       (map (fn [check] (check mconn deploy-conf deploy-info)))
       (not-any? false?)))

(defn max-wait-time-reached [starttime deployment-timeout-in-min]
  (let [timeout-as-millis (* deployment-timeout-in-min 60 1000)
        time-taken (- (System/currentTimeMillis) starttime)]
    (> time-taken timeout-as-millis)))

(defn wait-for-deployment [{{:keys [print-fn polling-interval-in-millis deployment-timeout-in-min]} :deploy-conf :as self} deploy-infos]
  (print-fn "Waiting for deployment to be finished...  ")
  (let [starttime (System/currentTimeMillis)]
    (loop [success? false]
      (if success?
        (print-fn "Deployment was successful!")
        (do
          (when (max-wait-time-reached starttime deployment-timeout-in-min)
            (throw (RuntimeException. "The deployment timed out")))
          (Thread/sleep polling-interval-in-millis)
          (recur
            (all-deployment-checks-successful? self deploy-infos)))))))

(defn print-pre-deployment-infos [{:keys [print-fn] :as deploy-conf} json version]
  (print-fn "Starting simple marathon deployment")
  (print-fn (format "Thats your Deploy-JSON for version %s: " version))
  (print-fn (json/write-str json))
  (let [current-version (get-current-version! deploy-conf)]
    (print-fn (str "Current version deployed is " (or current-version "not-known") " Your are deploying: " version))))

(defn handle-running-deployment [{:keys [mconn deploy-conf] :as self} json version]
  (if-let [marathon-deploy-version (mc/determine-deployment-version mconn (:id json))]
    (wait-for-deployment self (build-deployment-info json version marathon-deploy-version))
    (if (= version (get-current-version! deploy-conf))
      ((:print-fn deploy-conf) "No deployment started, version to deploy is the same as the one deployed")
      (throw (RuntimeException. (str "Error: No deployment was started for version " version))))))

(defn start-marathon-deployment [{:keys [mconn deploy-conf] :as self} {app-id :id :as json} version]
  (let [deployment-ongoing? (mc/deployment-exists-for? mconn app-id)]
    (assert (= false deployment-ongoing?) "There should not be a deployment already running")
    (print-pre-deployment-infos deploy-conf json version)
    (mc/create-new-app mconn json)
    (handle-running-deployment self json version)))

(def default-app-version-fn (fn []))

(defn deploy-conf-str
  [{:keys [print-fn deployment-timeout-in-min polling-interval-in-millis post-deployment-checks app-version-fn]}]
  (str "deployment-timeout-in-min: " deployment-timeout-in-min " "
       "polling-interval-in-millis: " polling-interval-in-millis " "
       "nr-of-post-deployment-checks: " (count post-deployment-checks) " "
       "app-version-fn?: " (not (= app-version-fn default-app-version-fn)) " "
       "custom-print-fn?: " (not (= println print-fn))))

(defrecord MarathonDeployment [mconn deploy-conf]
  checks/MarathonDeploymentCheckApi
  (with-app-version-check [self app-version-check-fn]
    (-> (update-in self [:deploy-conf :post-deployment-checks] conj checks/app-version-check)
        (update :deploy-conf assoc :app-version-fn app-version-check-fn)))

  (with-marathon-task-health-check [self]
    (update-in self [:deploy-conf :post-deployment-checks] conj checks/marathon-task-health-check))

  (with-marathon-app-version-check [self]
    (update-in self [:deploy-conf :post-deployment-checks] conj checks/marathon-app-version-check))

  (with-deployment-stopped-check [self]
    (update-in self [:deploy-conf :post-deployment-checks] conj checks/deployment-stopped-check))

  DeploymentAPI
  (start-deployment [self json version]
    ((:print-fn deploy-conf) (str "Using Deploy-Conf: " (deploy-conf-str deploy-conf)))
    (if (= (get-current-version! deploy-conf) version)
      ((:print-fn deploy-conf) (str "Version " version " is already deployed. Nothing to do."))
      (start-marathon-deployment self json version))))

(defn new-marathon-deployment
  [mconf & {:keys [print-fn deployment-timeout-in-min polling-interval-in-millis
                   post-deployment-checks app-version-fn]
            :or   {print-fn                   println
                   deployment-timeout-in-min  5
                   polling-interval-in-millis 2000
                   post-deployment-checks     []
                   app-version-fn             default-app-version-fn}}]
  (map->MarathonDeployment
    {:deploy-conf {:print-fn                   print-fn
                   :deployment-timeout-in-min  deployment-timeout-in-min
                   :polling-interval-in-millis polling-interval-in-millis
                   :post-deployment-checks     post-deployment-checks
                   :app-version-fn             app-version-fn}
     :mconn       (mc/new-marathon-connection mconf)}))


(defn new-marathon-deployment-with-deploy-conf [mconf deploy-conf]
  (apply (partial new-marathon-deployment mconf)
         (apply concat (seq deploy-conf))))

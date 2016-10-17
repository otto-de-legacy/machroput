(ns de.otto.machroput.marathon.deployment
  (:require
    [de.otto.machroput.marathon.connection :as mc]
    [clojure.data.json :as json]
    [de.otto.machroput.deploy-api :refer :all]))

(defn- deployment-started! [{deploying :deploying}]
  (reset! deploying true))

(defn- deployment-done! [{deploying :deploying}]
  (reset! deploying false))

(defn- deployment-running? [{deploying :deploying}]
  @deploying)

(defn false-check-and-return [print-fn cond msg]
  (when (not cond)
    (print-fn msg))
  cond)

(defn get-current-version [print-fn app-version-fn]
  (try
    (@app-version-fn)
    (catch Exception e
      (print-fn (str "An error occured when trying to execute the current-version-fn " (.getMessage e)))
      nil)))

(defn app-version-check [{:keys [app-version-fn print-fn deployment-info]}]
  (let [current-version (get-current-version print-fn app-version-fn)
        expected-version (:version @deployment-info)]
    (false-check-and-return print-fn
                            (= current-version expected-version)
                            (format "Version Check NOT ok! Found version %s on status page and not %s" current-version expected-version))))

(defn marathon-task-health-check [{:keys [mconn print-fn deployment-info]}]
  (let [app-conf (mc/get-app mconn (:id @deployment-info))
        tasks-running (get-in app-conf [:app :tasksRunning])
        tasks-healthy (get-in app-conf [:app :tasksHealthy])
        tasks-unhealth (get-in app-conf [:app :tasksUnhealthy])]
    (false-check-and-return print-fn
                            (and (= tasks-running (:instances @deployment-info))
                                 (= tasks-healthy tasks-running)
                                 (= 0 tasks-unhealth))
                            (format "Task Check was NOT ok! running: %s healthy: %s unhealthy: %s" tasks-running tasks-healthy tasks-unhealth))))

(defn marathon-app-version-check [{:keys [mconn print-fn deployment-info]}]
  (let [current-app-status (mc/get-app mconn (:id @deployment-info))
        current-app-version (get-in current-app-status [:app :version])]
    (false-check-and-return print-fn
                            (= current-app-version (:marathon-deploy-version @deployment-info))
                            (format "Marathon-Deploy-Version Check was NOT ok! App is not running latest deployment-version %s: %s" (:marathon-deploy-version @deployment-info) current-app-version))))

(defn deployment-stopped-check [{:keys [mconn print-fn deployment-info]}]
  (false-check-and-return print-fn
                          (= false (mc/deployment-still-running? mconn (:marathon-deploy-version @deployment-info)))
                          "Marathon-Deployment Check was NOT ok! The started Marathon-deployment is still running"))

(defn check-if-deployment-was-successful [{print-fn :print-fn :as self}]
  (let [all-checks-as-simple-fns (map #(partial % self) @(:post-deployment-checks self))]
    (when (not-any? false? (map (fn [f] (f)) all-checks-as-simple-fns))
      (print-fn "Deployment was successful!")
      (deployment-done! self))))

(defn max-wait-time-reached [starttime deployment-timeout-in-min]
  (let [timeout-as-millis (* deployment-timeout-in-min 60 1000)
        time-taken (- (System/currentTimeMillis) starttime)]
    (> time-taken timeout-as-millis)))

(defn wait-for-deployment [{:keys [print-fn mconf] :as self}]
  (print-fn "Waiting for deployment to be finished...  ")
  (let [polling-interval-in-millis (get mconf :polling-interval-in-millis 2000)
        deployment-timeout-in-min (get mconf :deployment-timeout-in-min 5)
        starttime (System/currentTimeMillis)]
    (while (deployment-running? self)
      (when (max-wait-time-reached starttime deployment-timeout-in-min)
        (throw (RuntimeException. "The deployment timed out")))
      (check-if-deployment-was-successful self)
      (Thread/sleep polling-interval-in-millis))))

(defn print-deployment-info [print-fn app-version-fn json version]
  (print-fn "Starting simple marathon deployment")
  (print-fn (format "Thats your Deploy-JSON for version %s: " version))
  (print-fn (json/write-str json))
  (let [current-version (get-current-version print-fn app-version-fn)]
    (print-fn (str "Current version deployed is " (or current-version "not-known") " Your are deploying: " version))))

(defn store-deployment-info [deployment-info json version marathon-deploy-version]
  (reset! deployment-info {:version                 version
                           :id                      (:id json)
                           :marathon-deploy-version marathon-deploy-version
                           :instances               (:instances json)}))

(defn handle-running-deployment [{:keys [mconn print-fn app-version-fn deployment-info] :as self} json version]
  (let [marathon-deploy-version (mc/determine-deployment-version mconn (:id json))]
    (store-deployment-info deployment-info json version marathon-deploy-version)
    (if-not (nil? marathon-deploy-version)
      (wait-for-deployment self)
      (if (= version (get-current-version print-fn app-version-fn))
        (print-fn "No deployment started, version to deploy is the same as the one deployed")
        (throw (RuntimeException. (str "Error: No deployment was started for version " version)))))))

(defn start-marathon-deployment [{:keys [mconn print-fn app-version-fn] :as self} {app-id :id :as json} version]
  (let [deployment-ongoing? (mc/deployment-exists-for? mconn app-id)]
    (assert (= false deployment-ongoing?) "There should not be a deployment already running")
    (deployment-started! self)
    (print-deployment-info print-fn app-version-fn json version)
    (mc/create-new-app mconn json)
    (handle-running-deployment self json version)))

(defprotocol MarathonDeploymentApi
  (with-app-version-check [self fn])
  (with-marathon-task-health-check [self])
  (with-marathon-app-version-check [self])
  (with-deployment-stopped-check [self]))

(defrecord MarathonDeployment [print-fn mconn deploying post-deployment-checks app-version-fn deployment-info]
  MarathonDeploymentApi
  (with-app-version-check [self fn]
    (reset! app-version-fn fn)
    (swap! post-deployment-checks conj app-version-check)
    self)

  (with-marathon-task-health-check [self]
    (swap! (:post-deployment-checks self) conj marathon-task-health-check)
    self)

  (with-marathon-app-version-check [self]
    (swap! (:post-deployment-checks self) conj marathon-app-version-check)
    self)

  (with-deployment-stopped-check [self]
    (swap! (:post-deployment-checks self) conj deployment-stopped-check)
    self)

  DeploymentAPI
  (start-deployment [self json version]
    (if (= (get-current-version print-fn app-version-fn) version)
      (print-fn (str "Version " version " is already deployed. Nothing to do."))
      (start-marathon-deployment self json version))))

(defn new-marathon-deployment
  ([mconf]
   (new-marathon-deployment mconf println))
  ([mconf print-fn]
   (map->MarathonDeployment
     {:print-fn               print-fn
      :mconf                  mconf
      :mconn                  (mc/new-marathon-connection mconf)
      :deploying              (atom false)
      :post-deployment-checks (atom [])
      :app-version-fn         (atom (fn []))
      :deployment-info        (atom nil)})))

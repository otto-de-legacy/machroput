(ns de.otto.machroput.marathon.checks
  (:require [de.otto.machroput.marathon.connection :as mc]))

(defprotocol MarathonDeploymentCheckApi
  (with-app-version-check [self fn])
  (with-marathon-task-health-check [self])
  (with-marathon-app-version-check [self])
  (with-deployment-stopped-check [self]))

(defn false-check-and-return [print-fn cond msg]
  (when (not cond)
    (print-fn msg))
  cond)

(defn get-current-version! [print-fn app-version-fn]
  (try
    (app-version-fn)
    (catch Exception e
      (print-fn (str "An error occured when trying to execute the current-version-fn " (.getMessage e)))
      nil)))

(defn app-version-check [_ {:keys [print-fn app-version-fn]} {expected-version :version}]
  (let [current-version (get-current-version! print-fn app-version-fn)]
    (false-check-and-return print-fn
                            (= current-version expected-version)
                            (format "Version Check NOT ok! Found version %s on status page and not %s" current-version expected-version))))

(defn marathon-task-health-check [mconn {:keys [print-fn]} {:keys [id instances]}]
  (let [app-conf (mc/get-app mconn id)
        tasks-running (get-in app-conf [:app :tasksRunning])
        tasks-healthy (get-in app-conf [:app :tasksHealthy])
        tasks-unhealth (get-in app-conf [:app :tasksUnhealthy])]
    (false-check-and-return print-fn
                            (and (= tasks-running instances)
                                 (= tasks-healthy tasks-running)
                                 (= 0 tasks-unhealth))
                            (format "Task Check was NOT ok! running: %s healthy: %s unhealthy: %s" tasks-running tasks-healthy tasks-unhealth))))

(defn marathon-app-version-check [mconn {:keys [print-fn]} {:keys [id marathon-deploy-version]}]
  (let [current-app-status (mc/get-app mconn (:id id))
        current-app-version (get-in current-app-status [:app :version])]
    (false-check-and-return print-fn
                            (= current-app-version marathon-deploy-version)
                            (format "Marathon-Deploy-Version Check was NOT ok! App is not running latest deployment-version %s: %s" marathon-deploy-version current-app-version))))

(defn deployment-stopped-check [mconn {:keys [print-fn]} {:keys [marathon-deploy-version]}]
  (false-check-and-return print-fn
                          (= false (mc/deployment-still-running? mconn marathon-deploy-version))
                          "Marathon-Deployment Check was NOT ok! The started Marathon-deployment is still running"))

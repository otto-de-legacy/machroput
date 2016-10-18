(ns de.otto.machroput.marathon.checks
  (:require [de.otto.machroput.marathon.connection :as mc]))

(defn false-check-and-return [print-fn cond msg]
  (when (not cond)
    (print-fn msg))
  cond)

(defn get-current-version! [{:keys [print-fn app-version-fn]}]
  (try
    (app-version-fn)
    (catch Exception e
      (print-fn (str "An error occured when trying to execute the current-version-fn " (.getMessage e)))
      nil)))

(defn app-version-check [{:keys [deploy-conf print-fn]} {expected-version :version}]
  (let [current-version (get-current-version! deploy-conf)]
    (false-check-and-return print-fn
                            (= current-version expected-version)
                            (format "Version Check NOT ok! Found version %s on status page and not %s" current-version expected-version))))

(defn marathon-task-health-check [{:keys [mconn print-fn]} {:keys [id instances]}]
  (let [app-conf (mc/get-app mconn id)
        tasks-running (get-in app-conf [:app :tasksRunning])
        tasks-healthy (get-in app-conf [:app :tasksHealthy])
        tasks-unhealth (get-in app-conf [:app :tasksUnhealthy])]
    (false-check-and-return print-fn
                            (and (= tasks-running instances)
                                 (= tasks-healthy tasks-running)
                                 (= 0 tasks-unhealth))
                            (format "Task Check was NOT ok! running: %s healthy: %s unhealthy: %s" tasks-running tasks-healthy tasks-unhealth))))

(defn marathon-app-version-check [{:keys [mconn print-fn]} {:keys [id marathon-deploy-version]}]
  (let [current-app-status (mc/get-app mconn (:id id))
        current-app-version (get-in current-app-status [:app :version])]
    (false-check-and-return print-fn
                            (= current-app-version marathon-deploy-version)
                            (format "Marathon-Deploy-Version Check was NOT ok! App is not running latest deployment-version %s: %s" marathon-deploy-version current-app-version))))

(defn deployment-stopped-check [{:keys [mconn print-fn]} {:keys [marathon-deploy-version]}]
  (false-check-and-return print-fn
                          (= false (mc/deployment-still-running? mconn marathon-deploy-version))
                          "Marathon-Deployment Check was NOT ok! The started Marathon-deployment is still running"))

(ns de.otto.machroput.marathon.checks
  (:require [de.otto.machroput.marathon.connection :as mc]))

(defprotocol MarathonDeploymentCheckApi
  (with-app-version-check [self fn])
  (with-marathon-task-health-check [self])
  (with-marathon-app-version-check [self])
  (with-deployment-stopped-check [self]))

(defn- fcheck [print-fn cond msg]
  (when (not cond)
    (print-fn msg))
  cond)

(defn- get-current-version! [print-fn app-version-fn]
  (try
    (app-version-fn)
    (catch Exception e
      (print-fn (str "An error occured when trying to execute the current-version-fn " (.getMessage e)))
      nil)))

(defn app-version-check [_ {:keys [print-fn app-version-fn]} {expected-version :version}]
  (let [current-version (get-current-version! print-fn app-version-fn)]
    (fcheck print-fn
            (= current-version expected-version)
            (format (str "Version Check was NOT ok!\n"
                         "Actual:   '%s' on status page\n"
                         "Expected: '%s' after deployment") current-version expected-version  ))))

(defn marathon-task-health-check [mconn {:keys [print-fn]} {:keys [id instances]}]
  (let [{{:keys [tasksUnhealthy tasksHealthy tasksRunning]} :app} (mc/get-app mconn id)]
    (fcheck print-fn
            (and (= tasksRunning instances)
                 (= tasksHealthy tasksRunning)
                 (= 0 tasksUnhealthy))
            (format (str "Task Check was NOT ok!\n" 
                         "Running: %s Healthy: %s Unhealthy: %s") tasksRunning tasksHealthy tasksUnhealthy))))

(defn marathon-app-version-check [mconn {:keys [print-fn]} {:keys [id marathon-deploy-version]}]
  (let [{{current-app-version :version} :app} (mc/get-app mconn id)]
    (fcheck print-fn
            (= current-app-version marathon-deploy-version)
            (format (str "Marathon-Deploy-Version Check was NOT ok!\n"
                         "App is not running latest deployment-version\n" 
                         "Currently running:      '%s'\n" 
                         "Deployed with Marathon: '%s'") current-app-version marathon-deploy-version ))))

(defn deployment-stopped-check [mconn {:keys [print-fn]} {:keys [marathon-deploy-version]}]
  (fcheck print-fn
          (= false (mc/deployment-still-running? mconn marathon-deploy-version))
          "Marathon-Deployment Check was NOT ok! The started Marathon-deployment is still running"))


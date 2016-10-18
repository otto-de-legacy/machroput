#machroput

This is a simple clojure-library to deploy apps to Marathon or Chronos.  It can be used standalone, but was developed with a [lambdacd](https://github.com/flosell/lambdacd)-integration in mind.
It is still in an early stage but e.g. already supports some easy post-deployment checks for marathon (like e.g. a task-health-check).

`[de.otto/machroput "1.0.0"]`

## Features included
* Deploy Chronos (v2.4.0)
* Deploy Marathon (v0.10.0)
* Post-Deployment-Checks for Marathon

## Examples

```clojure 
(ns my.name.space
  (:require 
    [de.otto.machroput.deploy-api :as dapi]
    [de.otto.machroput.marathon.checks :as checks]
    [de.otto.machroput.marathon.deployment :as msd]))

(def sample-marathon-config 
    {   :url "http://your.marathon.instance"
        :user "a-user"          ;marathon basic-auth-user, if required 
        :password "a-password"  ;marathon basic-auth-password, if required 
    })
    
(def sample-json ;a-marathon-json-as-map
    {   :id "your/marathon/id"
        :instances 3
        :mem 2024
        :cpus 1
        ...
    })
    
(def new-version "0.1.1") ; your version to be deployed
   

(defn deploy-marathon [mconf json version]
  (-> (msd/new-marathon-deployment
                     mconf
                     :print-fn println
                     :deployment-timeout-in-min 2
                     :polling-interval-in-millis 1000
                     :post-deployment-checks []
                     :app-version-fn (fn []))
      (checks/with-marathon-task-health-check)
      (checks/with-marathon-app-version-check)
      (checks/with-deployment-stopped-check)
      (checks/with-app-version-check (a-fn-returning-your-current-app-version))
      (dapi/start-deployment json version)))
      
; do the actual deployment
(deploy-marathon sample-marathon-config sample-json new-version)

```

To integrate the deployment with lambdacd do this:

```clojure 

(ns my.name.space
  (:require 
    [lambdacd.steps.support :as supp]
    [de.otto.machroput.marathon.checks :as checks]
    [de.otto.machroput.deploy-api :as dapi]
    [de.otto.machroput.marathon.deployment :as msd]))
    
(defn deploy-marathon [mconf json version print-fn]
  (-> (msd/new-marathon-deployment mconf :print-fn print-fn)
      (checks/with-marathon-task-health-check)
      (checks/with-marathon-app-version-check)
      (checks/with-deployment-stopped-check)
      (checks/with-app-version-check (a-fn-returning-your-current-app-version))
      (dapi/start-deployment json version)))
      
;; define lambdacd deployment-step
(defn start-deployment []
  (fn [args ctx]
    (let [printer (supp/new-printer)
          print-fn (fn [& msgs] (supp/print-to-output ctx printer (clojure.string/join msgs)))]
      (deploy-marathon sample-marathon-config sample-json new-version print-fn)
      {:status :success
       :out    (supp/printed-output printer)})))    

```



(ns de.otto.machroput.marathon.deployment-test
  (:require
    [de.otto.machroput.marathon.deployment :as mdep]
    [de.otto.machroput.marathon.connection :as mc]
    [de.otto.machroput.deploy-api :as dapi]
    [clojure.test :refer :all]))

(defrecord MarathonConectionMock [version deployment-still-running? deployment-exists? creations]
  mc/MarathonAPI
  (start [self])
  (create-new-app [self json]
    (swap! creations conj json))
  (get-apps [self])
  (get-app [self app-id])
  (get-app-versions [self app-id])
  (get-app-config [self app-id version])
  (get-deployments [self])

  mc/MaratohnAPIHelper
  (determine-deployment-version [_ _]
    version)
  (deployment-still-running? [_ _]
    deployment-still-running?)
  (deployment-exists-for? [_ _]
    deployment-exists?))

(def no-deployment-mock (->MarathonConectionMock nil false false (atom [])))
(def deployment-running-mock (->MarathonConectionMock "somid" true true (atom [])))
(defn catch-app-creations-mock [creations]
  (->MarathonConectionMock nil false false creations))

(deftest not-starting-deployments
  (testing "should NOT start a deployment if app-version is already present"
    (let [deploying? (atom false)
          mdepl (-> (mdep/new-marathon-deployment {})
                    (mdep/with-app-version-check (constantly "0.0.1"))
                    (assoc :mconn no-deployment-mock
                           :deploying deploying?))]
      (dapi/start-deployment mdepl {:id "someid"} "0.0.1")
      (is (= false @deploying?))))
  (testing "should NOT start a deployment if deployment is still running"
    (let [deploying? (atom false)
          mdepl (-> (mdep/new-marathon-deployment {})
                    (mdep/with-app-version-check (constantly "0.0.0"))
                    (assoc :mconn deployment-running-mock
                           :deploying deploying?))]
      (is (thrown? Throwable
                   (dapi/start-deployment mdepl {:id "someid"} "0.0.1"))))))

(deftest starting-deployments
  (testing "should start a new marathon deployment"
    (with-redefs [mdep/handle-running-deployment (constantly nil)]
      (let [deploying? (atom false)
            created-jsons (atom [])
            mdepl (-> (mdep/new-marathon-deployment {})
                      (mdep/with-app-version-check (constantly "0.0.0"))
                      (assoc :mconn (catch-app-creations-mock created-jsons)
                             :deploying deploying?))
            deployment-json {:id "someid"}]
        (dapi/start-deployment mdepl deployment-json "0.0.1")
        (is (= true @deploying?))
        (is (= [deployment-json] @created-jsons))))))

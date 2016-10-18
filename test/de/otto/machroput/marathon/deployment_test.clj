(ns de.otto.machroput.marathon.deployment-test
  (:require
    [de.otto.machroput.marathon.deployment :as mdep]
    [de.otto.machroput.marathon.mocks :as mocks]
    [de.otto.machroput.deploy-api :as dapi]
    [clojure.test :refer :all]
    [de.otto.machroput.marathon.checks :as checks]))

(deftest not-starting-deployments
  (testing "should NOT start a deployment if deployment is still running"
    (let [mdepl (-> (mdep/new-marathon-deployment {})
                    (mdep/with-app-version-check (constantly "0.0.0"))
                    (assoc :mconn mocks/deployment-running-mock))]
      (is (thrown? Throwable
                   (dapi/start-deployment mdepl {:id "someid"} "0.0.1"))))))

(deftest starting-deployments
  (testing "should start a new marathon deployment"
    (with-redefs [mdep/handle-running-deployment (constantly nil)]
      (let [created-jsons (atom [])
            mdepl (-> (mdep/new-marathon-deployment {})
                      (mdep/with-app-version-check (constantly "0.0.0"))
                      (assoc :mconn (mocks/catch-app-creations-mock created-jsons)))
            deployment-json {:id "someid"}]
        (dapi/start-deployment mdepl deployment-json "0.0.1")
        (is (= [deployment-json] @created-jsons))))))

(deftest wait-for-deployment-test
  (testing "should abort deployment after configured timeout"
    (with-redefs [mdep/all-deployment-checks-successful? (constantly nil)]
      (let [start-time (System/currentTimeMillis)
            ten-milli-in-min (/ 1 60 100)]
        (is (thrown? RuntimeException
                     (mdep/wait-for-deployment {:deploying   (atom true)
                                                :deploy-conf {:print-fn                   println
                                                              :polling-interval-in-millis 0
                                                              :deployment-timeout-in-min  ten-milli-in-min}}
                                               {})))
        (let [time-taken (- (System/currentTimeMillis) start-time)]
          (is (<= time-taken 20)))))))

(deftest simple-marathon-deployment
  (testing "should build a simple deployment"
    (let [app-check-fn (fn [] :bar)
          print-fn (fn [] :foo)
          standard-deployment (mdep/new-marathon-deployment
                                {}
                                :app-version-fn app-check-fn
                                :print-fn print-fn)]
      (is (= {:deployment-timeout-in-min  5
              :polling-interval-in-millis 2000
              :post-deployment-checks     []
              :app-version-fn             app-check-fn
              :print-fn                   print-fn}
             (:deploy-conf standard-deployment)))))

  (testing "should use default print-fn"
    (let [standard-deployment (mdep/new-marathon-deployment {})]
      (is (= println
             (get-in standard-deployment [:deploy-conf :print-fn])))))

  (testing "should use default app-version-fn"
    (let [standard-deployment (mdep/new-marathon-deployment {})
          default-app-version-fn (get-in standard-deployment [:deploy-conf :app-version-fn])]
      (is (= nil (default-app-version-fn)))))

  (testing "should build a simple deployment with with-app-version-check"
    (let [standard-deployment (-> (mdep/new-marathon-deployment {})
                                  (mdep/with-app-version-check :foo-check))]
      (is (= [checks/app-version-check]
             (get-in standard-deployment [:deploy-conf :post-deployment-checks])))
      (is (= :foo-check
             (get-in standard-deployment [:deploy-conf :app-version-fn])))))

  (testing "should build a simple deployment with with-marathon-task-health-check"
    (let [standard-deployment (-> (mdep/new-marathon-deployment {})
                                  (mdep/with-marathon-task-health-check))]
      (is (= [checks/marathon-task-health-check]
             (get-in standard-deployment [:deploy-conf :post-deployment-checks])))))

  (testing "should build a simple deployment with with-marathon-app-version-check"
    (let [standard-deployment (-> (mdep/new-marathon-deployment {})
                                  (mdep/with-marathon-app-version-check))]
      (is (= [checks/marathon-app-version-check]
             (get-in standard-deployment [:deploy-conf :post-deployment-checks])))))

  (testing "should build a simple deployment with with-deployment-stopped-check"
    (let [standard-deployment (-> (mdep/new-marathon-deployment {})
                                  (mdep/with-deployment-stopped-check))]
      (is (= [checks/deployment-stopped-check]
             (get-in standard-deployment [:deploy-conf :post-deployment-checks]))))))


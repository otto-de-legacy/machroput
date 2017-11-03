(ns de.otto.machroput.marathon.checks-test
  (:require
    [de.otto.machroput.marathon.deployment :as mdep]
    [de.otto.machroput.marathon.mocks :as mocks]
    [clojure.test :refer :all]
    [de.otto.machroput.marathon.checks :as checks]
    [de.otto.machroput.marathon.connection :as mc])
  (:import (de.otto.machroput.marathon.connection MaratohnAPIHelper)))

(def min-as-millis (/ 1 60 1000))

(deftest running-deployments-without-any-check
  (testing "should throw no exception if no post-deployment-check is configured"
    (let [hundred-millis-in-min (* min-as-millis 100)
          mdeployment           (-> (mdep/new-marathon-deployment {}
                                                                  :deployment-timeout-in-min hundred-millis-in-min
                                                                  :polling-interval-in-millis 10)
                                    (assoc :mconn (mocks/interactive-deployment-mock 10)))]
      (is (= nil (mdep/start-marathon-deployment mdeployment {} "0.0.1"))))))

(defrecord TestMarathonAPIHelper []
  MaratohnAPIHelper
  (determine-deployment-version [_ _] nil)
  (deployment-still-running? [_ _] nil)
  (deployment-exists-for? [_ _] true))

(deftest running-deployments-without-any-check
  (testing "should throw an exception if a deployment is already ongoing"
    (let [self {:mconn (map->TestMarathonAPIHelper {})}]
      (is (thrown? IllegalStateException (mdep/start-marathon-deployment self nil nil))))))


(deftest running-any-post-deployment-checks
  (testing "should start post-deployment-checks on deployment"
    (let [check-started       (atom nil)
          store-check-call-fn (fn [_ _ _] (reset! check-started :post-deployment-check-started!))
          mdeployment         (-> (mdep/new-marathon-deployment {}
                                                                :deployment-timeout-in-min 1
                                                                :polling-interval-in-millis 10
                                                                :post-deployment-checks [store-check-call-fn])
                                  (assoc :mconn (mocks/interactive-deployment-mock 100)))]
      (mdep/start-marathon-deployment mdeployment {} "0.0.1")
      (is (= :post-deployment-check-started! @check-started)))))


(deftest running-app-version-check
  (let [deployment-with-app-version-check (fn [& {:keys [deployment-timeout-in-min deploy-time app-version]}]
                                            (-> (mdep/new-marathon-deployment {}
                                                                              :deployment-timeout-in-min deployment-timeout-in-min
                                                                              :polling-interval-in-millis 10)
                                                (checks/with-app-version-check (fn [] @app-version))
                                                (assoc :mconn (mocks/interactive-deployment-mock deploy-time))))]

    (testing "should throw RTException if app-version does not change"
      (let [app-version (atom "0.0.1")]
        (is (thrown? RuntimeException
                     (-> (deployment-with-app-version-check
                           :deployment-timeout-in-min (* 30 min-as-millis)
                           :deploy-time 60
                           :app-version app-version)
                         (mdep/start-marathon-deployment {} "0.0.2"))))))

    (testing "should finish successful if app-version changes as expected"
      (let [app-version (atom "0.0.1")]
        (future
          (is (= nil (-> (deployment-with-app-version-check
                           :deployment-timeout-in-min (* 30 min-as-millis)
                           :deploy-time 10
                           :app-version app-version)
                         (mdep/start-marathon-deployment {} "0.0.2")))))
        (Thread/sleep 10)
        (reset! app-version "0.0.2")
        (Thread/sleep 30)))))

(deftest running-deployment-stopped-check
  (let [deployment-with-deployment-stopped-check (fn [& {:keys [deployment-timeout-in-min deploy-time]}]
                                                   (-> (mdep/new-marathon-deployment {}
                                                                                     :deployment-timeout-in-min deployment-timeout-in-min
                                                                                     :polling-interval-in-millis 10)
                                                       (checks/with-deployment-stopped-check)
                                                       (assoc :mconn (mocks/interactive-deployment-mock deploy-time))))]
    (testing "should throw an exception if deployment does not stop in time"
      (is (thrown? RuntimeException
                   (-> (deployment-with-deployment-stopped-check
                         :deployment-timeout-in-min (* 30 min-as-millis)
                         :deploy-time 60)
                       (mdep/start-marathon-deployment {} "0.0.1")))))

    (testing "should throw no exception if deployment stops in time"
      (is (= nil (-> (deployment-with-deployment-stopped-check
                       :deployment-timeout-in-min (* 30 min-as-millis)
                       :deploy-time 15)
                     (mdep/start-marathon-deployment {} "0.0.1")))))))


(deftest running-marathon-app-version-check
  (let [deployment-withapp-version-check (fn [& {:keys [deployment-timeout-in-min deploy-time]}]
                                           (-> (mdep/new-marathon-deployment {}
                                                                             :deployment-timeout-in-min deployment-timeout-in-min
                                                                             :polling-interval-in-millis 10)
                                               (checks/with-marathon-app-version-check)
                                               (assoc :mconn (mocks/interactive-deployment-mock
                                                               deploy-time
                                                               :app-transition [{:app {:version "marathon-0.0.1"}} {:app {:version "marathon-0.0.2"}}]))))]
    (testing "should throw an exception if marathon-app-version-check does not return valid response in time"
      (is (thrown? RuntimeException
                   (-> (deployment-withapp-version-check
                         :deployment-timeout-in-min (* 30 min-as-millis)
                         :deploy-time 60)
                       (mdep/start-marathon-deployment {} "0.0.1")))))

    (testing "should throw no exception if marathon-app-version-check returns valid response in time"
      (is (= nil (-> (deployment-withapp-version-check
                       :deployment-timeout-in-min (* 30 min-as-millis)
                       :deploy-time 15)
                     (mdep/start-marathon-deployment {} "some-app-version")))))))


(deftest running-marathon-task-health-check
  (let [deployment-with-marathon-task-health-check (fn [& {:keys [deployment-timeout-in-min deploy-time]}]
                                                     (-> (mdep/new-marathon-deployment {}
                                                                                       :deployment-timeout-in-min deployment-timeout-in-min
                                                                                       :polling-interval-in-millis 10)
                                                         (checks/with-marathon-task-health-check)
                                                         (assoc :mconn (mocks/interactive-deployment-mock
                                                                         deploy-time
                                                                         :app-transition [{:app {:version        "marathon-0.0.1"
                                                                                                 :tasksUnhealthy 1
                                                                                                 :tasksHealthy   1
                                                                                                 :tasksRunning   2}}
                                                                                          {:app {:version        "marathon-0.0.2"
                                                                                                 :tasksUnhealthy 0
                                                                                                 :tasksHealthy   1
                                                                                                 :tasksRunning   1}}]))))]
    (testing "should throw an exception if marathon-task-health-check does not return valid response in time"
      (is (thrown? RuntimeException
                   (-> (deployment-with-marathon-task-health-check
                         :deployment-timeout-in-min (* 30 min-as-millis)
                         :deploy-time 60)
                       (mdep/start-marathon-deployment {:instances 1} "0.0.1")))))

    (testing "should throw no exception if marathon-task-health-check returns valid response in time"
      (is (= nil
             (-> (deployment-with-marathon-task-health-check
                   :deployment-timeout-in-min (* 30 min-as-millis)
                   :deploy-time 15)
                 (mdep/start-marathon-deployment {:instances 1} "0.0.1")))))))

(deftest marathon-app-version-check-unit
  (testing "should call api with id and print error and return false"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/get-app (fn [_ id] (reset! call id))]
        (is (= false (checks/marathon-app-version-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:id "123" :marathon-deploy-version "000"})))
        (is (= "123" @call))
        (is (= (str "Marathon-Deploy-Version Check was NOT ok!\n"
                    "App is not running latest deployment-version\n"
                    "Currently running:      'null'\n"
                    "Deployed with Marathon: '000'") @error-print-call)))))

  (testing "should call api with id and print no error and return true"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/get-app (fn [_ id] (reset! call id)
                                 {:app {:version "000"}})]
        (is (= true (checks/marathon-app-version-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:id "123" :marathon-deploy-version "000"})))
        (is (= "123" @call))
        (is (= :no-call @error-print-call))))))

(deftest deployment-stopped-check-unit
  (testing "should call api with id and print error and return false"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/deployment-still-running? (fn [_ depl-version] (reset! call depl-version))]
        (is (= false (checks/deployment-stopped-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:marathon-deploy-version "000"})))
        (is (= "000" @call))
        (is (= "Marathon-Deployment Check was NOT ok! The started Marathon-deployment is still running" @error-print-call)))))

  (testing "should call api with id and print no error and return true"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/deployment-still-running? (fn [_ depl-version] (reset! call depl-version)
                                                   false)]
        (is (= true (checks/deployment-stopped-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:marathon-deploy-version "000"})))
        (is (= "000" @call))
        (is (= :no-call @error-print-call))))))

(deftest marathon-task-health-check-unit
  (testing "should call api with id and print error and return false"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/get-app (fn [_ id] (reset! call id))]
        (is (= false (checks/marathon-task-health-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:id "123" :instances 1})))
        (is (= "123" @call))
        (is (= (str "Task Check was NOT ok!\n"
                    "Running: null Healthy: null Unhealthy: null") @error-print-call)))))

  (testing "should call api with id and print no error and return true"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (with-redefs [mc/get-app (fn [_ id] (reset! call id)
                                 {:app {:tasksUnhealthy 0
                                        :tasksHealthy   1
                                        :tasksRunning   1}})]
        (is (= true (checks/marathon-task-health-check {} {:print-fn (fn [e] (reset! error-print-call e))} {:id "123" :instances 1})))
        (is (= "123" @call))
        (is (= :no-call @error-print-call))))))

(deftest app-version-check-unit
  (testing "should call app-version-fn and print error and return false"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (is (= false (checks/app-version-check {} {:app-version-fn (fn [] (reset! call :call)) :print-fn (fn [e] (reset! error-print-call e))} {:version "0.0.1"})))
      (is (= :call @call))
      (is (= (str "Version Check was NOT ok!\n"
                  "Actual:   ':call' on status page\n"
                  "Expected: '0.0.1' after deployment") @error-print-call))))

  (testing "should call app-version-fn and print no error and return true"
    (let [call             (atom :no-call)
          error-print-call (atom :no-call)]
      (is (= true (checks/app-version-check {} {:app-version-fn (fn [] (reset! call :call)
                                                                  "0.0.1") :print-fn (fn [e] (reset! error-print-call e))} {:version "0.0.1"})))
      (is (= :call @call))
      (is (= :no-call @error-print-call)))))

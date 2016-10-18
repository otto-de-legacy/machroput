(ns de.otto.machroput.marathon.mocks
  (:require [de.otto.machroput.marathon.connection :as mc]))



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


(defn update-interactive-deployment-state! [deploy-time state]
  (when-let [started-time (:deployment-started-at @state)]
    (let [time-taken (- (System/currentTimeMillis) started-time)]
      (when (> time-taken deploy-time)
        (swap! state dissoc :deployment-started-at))))
  nil)

(defrecord InteractiveMarathonConectionMock [deploy-id deploy-time state app-transition]
  mc/MarathonAPI
  (start [self])
  (create-new-app [self json]
    (update-interactive-deployment-state! deploy-time state)
    (swap! state assoc :deployment-started-at (System/currentTimeMillis)))
  (get-apps [self]
    (update-interactive-deployment-state! deploy-time state))
  (get-app [self app-id]
    (update-interactive-deployment-state! deploy-time state)
    (if (not (nil? (:deployment-started-at @state)))
      (first app-transition)
      (second app-transition)))
  (get-app-versions [self app-id]
    (update-interactive-deployment-state! deploy-time state))
  (get-app-config [self app-id version]
    (update-interactive-deployment-state! deploy-time state))
  (get-deployments [self]
    (update-interactive-deployment-state! deploy-time state))

  mc/MaratohnAPIHelper
  (determine-deployment-version [_ appid]
    (update-interactive-deployment-state! deploy-time state)
    (get-in (second app-transition) [:app :version]))

  (deployment-still-running? [_ depl-version]
    (update-interactive-deployment-state! deploy-time state)
    (not (nil? (:deployment-started-at @state))))

  (deployment-exists-for? [_ app-id]
    (update-interactive-deployment-state! deploy-time state)
    (not (nil? (:deployment-started-at @state)))))

(def no-deployment-mock (->MarathonConectionMock nil false false (atom [])))
(def deployment-running-mock (->MarathonConectionMock "somid" true true (atom [])))

(def default-app-transition
  [{:app {:version "old-version"}} {:app {:version "new-version"}}])

(defn interactive-deployment-mock [deploy-time & {:keys [app-transition]
                                                  :or   {app-transition default-app-transition}}]
  (->InteractiveMarathonConectionMock "deploy-id" deploy-time (atom {}) app-transition))

(defn catch-app-creations-mock [creations]
  (->MarathonConectionMock nil false false creations))


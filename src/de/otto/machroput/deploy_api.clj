(ns de.otto.machroput.deploy-api)

(defprotocol DeploymentAPI
  (start-deployment [self json version]))

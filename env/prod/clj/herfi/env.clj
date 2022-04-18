(ns herfi.env
  (:require
    [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[herfi started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[herfi has shut down successfully]=-"))
   :middleware identity})

(ns herfi.env
  (:require
    [clojure.tools.logging :as log]
    [herfi.dev-middleware :refer [wrap-dev]]
    [selmer.parser :as parser]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[herfi started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[herfi has shut down successfully]=-"))
   :middleware wrap-dev})

(ns cljs.user
  (:require
    [herfi.scene.core :as core]))

(defn reset []
  (core/reset))

(defn reload []
  (js/location.reload))

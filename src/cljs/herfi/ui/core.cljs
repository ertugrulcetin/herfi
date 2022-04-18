(ns herfi.ui.core
  (:require
    [breaking-point.core :as bp]
    [herfi.common.config :as config]
    [herfi.ui.events :as events]
    [herfi.ui.views :as views]
    [re-frame.core :refer [dispatch-sync clear-subscription-cache!]]
    [reagent.dom :as rdom]))

(defn dev-setup []
  (when config/dev?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (dispatch-sync [::events/initialize-db])
  (dispatch-sync [::bp/set-breakpoints
                  {:breakpoints [:mobile
                                 768
                                 :tablet
                                 992
                                 :small-monitor
                                 1200
                                 :large-monitor]
                   :debounce-ms 166}])
  (dev-setup)
  (mount-root))

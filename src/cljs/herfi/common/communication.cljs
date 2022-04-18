(ns herfi.common.communication
  (:require
    [cljs.core.async :as a])
  (:require-macros
    [herfi.scene.macros :refer [go-loop-sub]]))

(def exit-chs (atom {}))
(def event-bus (a/chan))
(def event-bus-pub (a/pub event-bus first))

(defn add-to-exit-ch [key ch]
  (swap! exit-chs assoc key ch))

(defn terminate-all-chs []
  (doseq [ch (vals @exit-chs)]
    (a/put! ch [:exit])
    (a/close! ch))
  (reset! exit-chs {}))

(defn fire [id data]
  (when (@exit-chs id)
    (a/put! event-bus [id data])))

(defn on [id f]
  (go-loop-sub event-bus-pub id [_ data]
               (f data)))

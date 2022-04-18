(ns herfi.scene.macros
  (:require
    [cljs.core.async.macros :refer [go-loop]]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def three-objects
  (->> "node_modules/three/src"
       io/file
       file-seq
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getFileName (.toPath %)) ".js"))
       (mapv #(str (.getFileName (.toPath %))))
       (filter #(Character/isUpperCase (first %)))
       (map #(subs % 0 (str/index-of % ".js")))
       (map #(str "THREE/" %))))

(defmacro go-loop-sub [pub key binding & body]
  `(let [ch# (cljs.core.async/chan)]
     (herfi.common.communication/add-to-exit-ch ~key ch#)
     (cljs.core.async/sub ~pub ~key ch#)
     (go-loop []
       (let [~binding (cljs.core.async/<! ch#)]
         (when-not (= (first ~binding) :exit)
           (try
             ~@body
             (catch js/Error e
               (js/console.error "go-loop")
               (js/console.error e)))
           (recur))))))

(defmacro fnd [id & body]
  `(herfi.scene.ecs/update-script-update-fn ~id
                                            (fn [~'delta]
                                              ~@body)))

(defmacro three? [obj]
  `(try
     (or (instance? THREE/Object3D ~obj)
         ~@(map
             (fn [x]
               `(= ~(symbol x) (.-constructor ~obj))) three-objects))
     (catch js/Error _#
       false)))

(ns herfi.scene.db
  (:refer-clojure :exclude [swap! get set update update-in])
  (:require
    [applied-science.js-interop :as j]))

(def default {:name "Ancient PvP"})

(defonce ^:private app-db (atom default))

(defn reset-db! []
  (reset! app-db default))

(defn get-scene []
  (:scene @app-db))

(defn swap!
  ([f]
   (reset! app-db (f (.-state app-db))))
  ([f x]
   (reset! app-db (f (.-state app-db) x)))
  ([f x y]
   (reset! app-db (f (.-state app-db) x y)))
  ([f x y & more]
   (reset! app-db (apply f (.-state app-db) x y more))))

(defn init [states]
  (reset-db!)
  (swap! merge states))

(defn get
  ([] @app-db)
  ([& keys] (get-in @app-db keys)))

(defn get!
  "This fn is able to access js objects as well. It should be relatively slow."
  [& keys*]
  (let [db @app-db
        result (reduce (fn [r k]
                         (if (nil? r)
                           (reduced nil)
                           (if-let [r* (or (k r) (j/get r k))]
                             r*
                             r))) db keys*)]
    (if (and (map? result) (= (cljs.core/set (keys db)) (cljs.core/set (keys result))))
      nil
      result)))

(defn get-entity
  ([id]
   (let [id (if (string? id) (keyword id) id)]
     (-> @app-db :entities id)))
  ([id & args]
   (let [id (if (string? id) (keyword id) id)]
     (get-in @app-db (into [:entities id] args)))))

(defn get-entity-obj! [id & ks]
  (let [id (if (string? id) (keyword id) id)]
    (apply get! (into [:entities id :render :obj] ks))))

(defn print-obj! [id]
  (js/console.log (get-entity-obj! id)))

(defn set [k v]
  (swap! assoc k v))

(defn set-in [& args]
  (swap! assoc-in (drop-last args) (last args)))

(defn set-entity [id k v]
  (set-in :entities id k v))

(defn update [k f & args]
  (swap! (fn [s]
           (apply cljs.core/update (into [s k f] args)))))

(defn update-in [ks f & args]
  (swap! (fn [s]
           (apply cljs.core/update-in (into [s ks f] args)))))

(defn update-entity [id f & args]
  (apply update-in (into [[:entities id] f] args)))

(defn update-in-entity [id ks f & args]
  (apply update-in (into [(concat [:entities id] ks) f] args)))

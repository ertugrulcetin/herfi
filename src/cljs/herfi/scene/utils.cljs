(ns herfi.scene.utils
  (:require
    [applied-science.js-interop.impl :refer [assoc-in* wrap-key]]))

(defn dissoc-in
  ([m ks]
   (if-let [[k & ks] (seq ks)]
     (if (seq ks)
       (let [v (dissoc-in (get m k) ks)]
         (if (empty? v)
           (dissoc m k)
           (assoc m k v)))
       (dissoc m k))
     m))
  ([m ks & kss]
   (if-let [[ks' & kss] (seq kss)]
     (recur (dissoc-in m ks) ks' kss)
     (dissoc-in m ks))))

(defn- paths [m]
  (letfn [(paths* [ps ks m]
            (reduce-kv
              (fn [ps k v]
                (if (map? v)
                  (paths* ps (conj ks k) v)
                  (conj ps (conj ks k))))
              ps
              m))]
    (paths* () [] m)))

(defn merge! [obj m*]
  (doseq [p (paths m*)]
    (assoc-in* obj (mapv wrap-key p) (get-in m* p))))

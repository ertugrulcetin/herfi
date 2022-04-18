(ns herfi.scene.procgen)

(defn seed-from [source]
  ;; seed random number generator from URL hash fragment
  (let [hashfrag (-> source (.substr 1))
        hashfrag (if (= hashfrag "") (-> js/Math .random .toString (.split ".") .pop) hashfrag)]
    (.seedrandom js/Math "9912558260783595" #_hashfrag)))

(defn seed-from-hash []
  (aset js/window "location" "hash"
        (seed-from
          (aget js/window "location" "hash"))))

(defn choice [a]
  (nth a (int (* (js/Math.random) (count a)))))


(ns herfi.ui.subs
  (:require
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::db
  (fn [db _]
    db))

(reg-sub
  ::name
  (fn [db]
    (:name db)))

(reg-sub
  ::messages
  (fn [db]
    (:messages db)))

(reg-sub
  ::selected-character
  (fn [db]
    (when-let [name (:name (:selected-character db))]
      (assoc (get-in db [:world name]) :name name))))

(reg-sub
  ::skills
  (fn [db _]
    (->> db
         :skills
         vals
         (sort-by :index))))

(reg-sub
  ::position
  (fn [db]
    (:position db)))

(reg-sub
  ::renderer-info
  (fn [db]
    (:renderer-info db)))

(reg-sub
  ::hp
  (fn [db]
    (:hp db)))

(reg-sub
  ::mp
  (fn [db]
    (:mp db)))

(reg-sub
  ::damages
  (fn [db]
    (:damages db)))

(reg-sub
  ::logging?
  (fn [db]
    (:logging? db)))

(reg-sub
  ::error-msg
  (fn [db]
    (:error-msg db)))

(reg-sub
  ::login-done?
  (fn [db]
    (:login-done? db)))

(reg-sub
  ::ping
  (fn [db]
    (when (and (:world db) (:username db))
      (get-in (:world db) [(:username db) :ping]))))

(reg-sub
  ::username
  (fn [db]
    (:username db)))

(reg-sub
  ::show-score-board?
  (fn [db]
    (:show-score-board? db)))

(reg-sub
  ::scoreboard
  (fn [db]
    (let [world (:world db)]
      (when (seq world)
        (->> world
             (reduce-kv (fn [acc k v] (assoc acc k (assoc v :username k))) {})
             (vals)
             (map (juxt :kills :deaths identity))
             (group-by first)
             (vals)
             (mapcat #(sort-by second %))
             (sort-by first #(compare %2 %1))
             (map #(nth % 2)))))))

(reg-sub
  ::killed-by-messages
  (fn [db]
    (:killed-by-messages db)))

(reg-sub
  ::chat-enabled?
  (fn [db]
    (:chat-enabled? db)))

(reg-sub
  ::chat
  (fn [db]
    (:chat db)))

(reg-sub
  ::ws-conn-lost?
  (fn [db]
    (:ws-conn-lost? db)))

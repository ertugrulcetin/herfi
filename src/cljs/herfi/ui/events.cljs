(ns herfi.ui.events
  (:require
    [ajax.core :as ajax]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [com.rpl.specter :as sp :refer-macros [setval]]
    [day8.re-frame.http-fx]
    [herfi.character :as common.char]
    [herfi.common.communication :refer [fire]]
    [herfi.common.config :as config]
    [herfi.ui.db :as db]
    [re-frame.core :refer [reg-event-db reg-event-fx reg-fx]]))

(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  ::add-msg
  (fn [db [_ msg]]
    (update db :messages (fnil conj []) msg)))

(reg-event-db
  ::set-selected-character
  (fn [db [_ data]]
    (assoc db :selected-character data)))

(reg-event-fx
  ::add-loading-skill
  (fn [{:keys [db]} [_ skill]]
    {:db (assoc-in db [:skills skill :loading?] true)
     :dispatch-later {:ms (-> skill common.char/skills :duration (* 1000))
                      :dispatch [::remove-loading-skill skill]}}))

(reg-event-db
  ::remove-loading-skill
  (fn [db [_ skill]]
    (assoc-in db [:skills skill :loading?] false)))

(reg-event-db
  ::set-position
  (fn [db [_ pos]]
    (assoc db :position pos)))

(reg-event-db
  ::set-renderer-info
  (fn [db [_ data]]
    (assoc db :renderer-info {:calls (j/get data :calls)
                              :triangles (j/get data :triangles)
                              :frame (j/get data :frame)
                              :points (j/get data :points)
                              :lines (j/get data :lines)})))

(reg-event-db
  ::set-world
  (fn [db [_ data]]
    (assoc db :world data)))

(reg-event-db
  ::set-hp
  (fn [db [_ hp]]
    (assoc db :hp hp)))

(reg-event-db
  ::set-mp
  (fn [db [_ hp]]
    (assoc db :mp hp)))

(reg-event-fx
  ::add-damage
  (fn [{:keys [db]} [_ skill]]
    {:db (update db :damages (fnil conj []) skill)
     :dispatch-later {:ms 4000
                      :dispatch [::remove-damage skill]}}))

(reg-event-db
  ::remove-damage
  (fn [db [_ skill]]
    (let [idx (.indexOf (:damages db) skill)]
      (cond->> db
        (not= -1 idx) (setval [:damages idx] sp/NONE)))))

(reg-event-db
  ::respawn
  (fn [db _]
    (let [db (assoc db :hp 100 :mp 100)]
      (->> db
           (setval [:skills sp/MAP-VALS :loading?] false)
           (setval [:damages sp/ALL] sp/NONE)))))

(reg-event-db
  ::set-input-username
  (fn [db [_ username]]
    (assoc db :input-username username)))

(reg-event-db
  ::set-resolution
  (fn [db [_ resolution]]
    (assoc db :resolution resolution)))

(reg-event-fx
  ::enter-world
  (fn [{:keys [db]} _]
    (let [username (:input-username db)
          resolution (:resolution db :high)]
      {:db (assoc db :logging? true)
       :http-xhrio {:method :post
                    :uri (str config/api-url "/check_username")
                    :params {:username username}
                    :timeout 30000
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::enter-world-success username resolution]
                    :on-failure [::enter-world-failed]}})))

(reg-event-fx
  ::enter-world-success
  (fn [{:keys [db]} [_ username resolution {:keys [success?]}]]
    (if success?
      {:db (assoc db :username username
                  :resolution resolution)
       ::init-scene {:username username
                     :resolution resolution}}
      {:db (assoc db :logging? false
                  :error-msg "This username is already taken")})))

(reg-event-db
  ::enter-world-failed
  (fn [db [_ {:keys [response]}]]
    (assoc db :logging? false
           :error-msg (or (:msg response) "Something went wrong. Can't reach the server."))))

(reg-event-db
  ::clear-error-msg
  (fn [db _]
    (dissoc db :error-msg)))

(reg-fx
  ::init-scene
  (fn [data]
    (fire :init-scene data)))

(reg-event-db
  ::close-login-modal
  (fn [db _]
    (assoc db :login-done? true)))

(reg-event-db
  ::set-score-board-visibility
  (fn [db [_ type]]
    (assoc db :show-score-board? (= type :visible))))

(reg-event-db
  ::add-killed-by
  (fn [db [_ data]]
    (update db :killed-by-messages (fn [messages]
                                     (let [info (select-keys data [:killer :slaughtered])]
                                       (if (empty? messages)
                                         [info]
                                         (vec (take-last 5 (conj messages info)))))))))

(reg-event-db
  ::add-msg-to-chat
  (fn [db [_ data]]
    (update db :chat (fn [messages]
                       (if (empty? messages)
                         [data]
                         (vec (take-last 100 (conj messages data))))))))

(reg-event-db
  ::toggle-chat
  (fn [db _]
    (update db :chat-enabled? not)))

(reg-event-db
  ::set-chat-message
  (fn [db [_ msg]]
    (assoc db :chat-message msg)))

(reg-fx
  ::send-chat-message!
  (fn [data]
    (fire :chat-message data)))

(reg-event-fx
  ::send-chat-message
  (fn [{:keys [db]}]
    (cond-> {:db (assoc db :chat-message "")}
      (not (str/blank? (:chat-message db))) (assoc ::send-chat-message! (:chat-message db)))))

(reg-event-db
  ::show-connection-lost-modal
  (fn [db _]
    (assoc db :ws-conn-lost? true)))

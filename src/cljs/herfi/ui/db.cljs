(ns herfi.ui.db
  (:require
    [herfi.character :refer [skills]]))

;; TODO optimize images
(def default-db
  {:name "re-frame"
   :skills skills
   :hp 100
   :mp 100
   :chat-enabled? false
   :chat [{:sender "System" :chat-message "Press ENTER to chat"}
          {:sender "System" :chat-message "Press TAB to check scoreboard"}
          {:sender "System" :chat-message "Adjust camera with MOUSE RIGHT CLICK"}]})

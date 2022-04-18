(ns herfi.middleware
  (:require
    [clojure.tools.logging :as log]
    [herfi.config :refer [env]]
    [herfi.env :refer [defaults]]
    [herfi.layout :refer [error-page]]
    [herfi.middleware.formats :as formats]
    [manifold.deferred :as d]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        {:status 500
         :body {:msg (.getMessage t)}}))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn wrap-deferred-handler
  "Chains the Deferred response of handler to respond and raise callbacks."
  [handler]
  (fn [request respond raise]
    (-> (d/chain (handler request)
                 respond)
        (d/catch raise))))

(defn wrap-ring-async-handler
  "Converts given asynchronous Ring handler to Aleph-compliant handler."
  [handler]
  (fn [request]
    (let [response (d/deferred)]
      (handler request #(d/success! response %) #(d/error! response %))
      response)))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-gzip
      (wrap-cors
        :access-control-allow-origin [#".*"]
        :access-control-allow-methods [:get :put :post :delete :patch])
      wrap-deferred-handler
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in [:session :store] (ttl-memory-store (* 60 30)))))
      wrap-ring-async-handler
      wrap-internal-error))

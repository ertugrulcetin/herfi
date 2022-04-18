(ns herfi.dev-middleware
  (:require
    [prone.middleware :refer [wrap-exceptions]]
    [ring.middleware.reload :refer [wrap-reload]]
    [selmer.middleware :refer [wrap-error-page]]))

(defn wrap-dev
  [handler]
  (-> handler
      wrap-reload
      wrap-error-page
      (wrap-exceptions {:app-namespaces ['herfi]})))

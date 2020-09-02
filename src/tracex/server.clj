(ns tracex.server
  (:require [org.httpkit.server :as http-server]
            [compojure.core :as compojure :refer [GET POST]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.core.async :refer [go-loop] :as async]))

(def server (atom nil))

(defn build-websocket []
  ;; TODO: move all this stuff to sierra components or something like that
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter)
                                    {:csrf-token-fn nil})]

    {:ws-routes (compojure/routes (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
                                  (POST "/chsk" req (ajax-post-fn                req)))
     :ws-send-fn send-fn
     :ch-recv ch-recv
     :connected-uids-atom connected-uids}))

(defn build-routes [opts]
  (compojure/routes
   (GET  "/" req {:status 200
                  :body "GREAT!"})))

(defn handle-ws-message [{:keys [event]}]
  (println "Server handling ws event " event))

(defn -main [& args]
  (let [{:keys [ws-routes ws-send-fn ch-recv connected-uids-atom]} (build-websocket)
        port 8080]

    (go-loop []
      (try
        (handle-ws-message (async/<! ch-recv))
        (catch Exception e
          (println "ERROR handling ws message")))
      (recur))

    (reset! server (http-server/run-server (-> (compojure/routes ws-routes (build-routes {:port port}))
                                              (wrap-cors :access-control-allow-origin [#"http://localhost:9500"]
                                                         :access-control-allow-methods [:get :put :post :delete])
                                              wrap-keyword-params
                                              wrap-params)
                                          {:port port}))))

(ns flow-storm.tracer
  (:require  [taoensso.sente  :as sente]))

(defonce send-fn-a (atom nil))

(def ^:dynamic *flow-id* nil)
(def ^:dynamic *form-id* nil)

(defn ws-send [event]
  ((or @send-fn-a println) event))

(defn init-trace [traced-form-id form]
  (ws-send [:flow-storm/init-trace {:flow-id *flow-id* :form-id traced-form-id :form (pr-str form)}]))

(defn trace-and-return [result {:keys [coor outer-form?] :as extras} orig-form]
  (ws-send [:flow-storm/add-trace (cond-> {:flow-id *flow-id*
                                           :form-id *form-id*
                                           :coor coor
                                           :result (binding [*print-length* (or *print-length* 50)]
                                                     (pr-str result))}
                                    outer-form? (assoc :outer-form? true))])
  result)

(defn bound-trace [symb val {:keys [coor] :as extras}]
  (ws-send [:flow-storm/add-bind-trace {:flow-id *flow-id* :form-id *form-id* :coor coor :symbol (name symb) :value (binding [*print-length* (or *print-length* 50)]
                                                                                                                      (pr-str val))}]))

(defn connect
  ([] (connect nil))
  ([{:keys [host port]}]
   (let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket-client! "/chsk"  nil {:type :ws
                                                                                               :client-id "tracer"
                                                                                               :host (or host "localhost")
                                                                                               :port (or port 7722)})]
     (reset! send-fn-a send-fn))))

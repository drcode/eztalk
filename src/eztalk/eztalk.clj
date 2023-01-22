(ns eztalk.eztalk
  (:require [clojure.edn :as ed]
            [clojure.core.async :as as]
            [clojure.stacktrace :as st]
            [ezzmq.core :as zm]))

(def eztalk-port 13245)

(def kill-atoms (atom []))

(def init-cmd "___init___")

(defn extract-address [s]
  (let [[_ addr] (re-matches #"tcp:\/\/(.*):.*" s)]
    addr))

(defn start
  ([fun other-address port]
   (when (and (not other-address) (not= port eztalk-port))
     (throw (Exception. "currently, you must use the default eztalk port if you are not providing the address of another node")))
   (let [node {:my-socket    (zm/socket :rep {:bind (str "tcp://*:" port)})
               :other-socket (atom (when other-address
                                     (zm/socket :req {:connect (str "tcp://" other-address ":" eztalk-port)})))
               :killed       (atom false)}]
     (when other-address
       (zm/send-msg @(:other-socket node) (str init-cmd port))
       (zm/receive-msg @(:other-socket node) {:stringify true}))
     (swap! kill-atoms conj (:killed node))
     (when-not other-address
       (println "listening on socket for other peer..."))
     (as/thread (do (while (not @(:killed node))
                      (when-let [s (zm/receive-msg (:my-socket node)
                                                   {:stringify true
                                                    :timeout   300})]
                        (if (= (apply str (take (count init-cmd) (first s))) init-cmd)
                          (do (let [addr (extract-address (.getLastEndpoint (:my-socket node)))]
                                (println "found other peer" addr)
                                (reset! (:other-socket node) (zm/socket :req {:connect (str "tcp://" other-address ":" (apply str (drop (count init-cmd) (first s))))}))))
                          (fun (ed/read-string (first s))))
                        (zm/send-msg (:my-socket node) "gotit")))
                    (println "listening thread killed")))
     (while (and (not @(:other-socket node)) (not @(:killed node)))
       (Thread/sleep 100))
     (fn [data]
       (zm/send-msg @(:other-socket node) (pr-str data)) 
       (zm/receive-msg @(:other-socket node) {:stringify true}))))
  ([fun other-address]
   (start fun other-address eztalk-port))
  ([fun]
   (start fun nil)))

(defmacro with-eztalk [& body]
  `(zm/with-new-context (try ~@body
                             (catch Exception e#
                               (println "socket exception")
                               (st/print-stack-trace e#))
                             (finally (doseq [k# @kill-atoms]
                                        (reset! k# true))
                                      (reset! kill-atoms [])
                                      (Thread/sleep 500)))))

(defn test-all [] ;;this test is a little more complicated than explained in the README because both nodes are on the same machine
  (with-eztalk (let [node1 (atom nil)]
                 (as/thread (reset! node1
                                    (start (fn [data]
                                             (println "node1 got:" data)))))
                 (Thread/sleep 100)
                 (let [node2 (start (fn [data]
                                      (println "node2 got:" data))
                                    "127.0.0.1"
                                    55555)]
                   (while (not @node1)
                     (Thread/sleep 100))
                   (node2 "greetings from node2")
                   (@node1 "greetings from node1")))))


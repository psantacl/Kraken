(ns org.wol.kraken.core
  (:require [aleph.http                   :as  ahttp]
            [clojure.contrib.classpath    :as cp]
            [clojure.contrib.duck-streams :as ds]
            [clojure.contrib.str-utils    :as str-utils]
            [clojure.contrib.json         :as json]
            [org.wol.kraken.sequencer    :as sequencer]
            [org.wol.kraken.audio        :as audio]
            [org.wol.kraken.logging      :as log]
            [wol-utils.core               :as wol-utils])
  (:use [lamina.core]
        [net.cgrand.moustache]))

(def *server* (atom nil))
(def *web-socket-list* (atom []))

(defn play-sounds-for-step [step]
  (let [step-instructions (get @sequencer/*pattern* step)
        instrument-dispatch {0 audio/*Drum1* 1 audio/*Drum2*
                             2 audio/*Drum3* 3 audio/*Drum4*}]
    (dotimes [i 4]
      (if (nth step-instructions i)
        (do
          (log/info (format "playing sound %d for step %d" i step))
          (audio/play-sound-from-atom (get instrument-dispatch i)))))))

;;(.setTempoInBPM sequencer/*sequencer* bpm)
(defn tempo-change [new-tempo]
  (let [bpm (* 1.0 new-tempo)]
    (.setTempoFactor @sequencer/*sequencer*
                     (/ bpm  120))))

(defn send-to-other-clients [current-channel msg]
  (doseq [ws (filter #(not (= %1 current-channel)) @*web-socket-list*)]
    (log/info (format "Enqueing %s from %s to %s" msg current-channel ws))
    (enqueue ws msg)))

(defn wsocket-receive [ch]
  (fn [msg]
    (if-not msg
      (do
        (log/info "Received nil from WebSocket")
        (close ch)
        (reset! *web-socket-list* (filter #(not (= %1 ch)) @*web-socket-list*)))
      (let [command (:command (json/read-json msg))
            payload (:payload  (json/read-json msg))]
        (cond
          (= command "play")
          (do
            (log/info "Starting Sequencer")
            (.start @sequencer/*sequencer*))

          (= command "stop")
          (do
            (log/info "Stoping Sequencer")
            (.stop @sequencer/*sequencer*))

          (= command "tempo")
          (do
            (log/info (format "Tempo Change to %s bpm" (:bpm payload)))
            (tempo-change (:bpm payload))
            (send-to-other-clients ch msg))

          (= command "patternChange")
          (do
            (sequencer/step-selected (java.lang.Integer/parseInt (:step payload))
                                     (java.lang.Integer/parseInt (:instrument payload))
                                     (:checked payload))
            (send-to-other-clients ch msg))

          (= command "volume")
          (do
            (log/info (format "Volume Change on instrument %s to %s"
                              (:instrument payload) (:value payload)))
            (audio/volume-change payload)
            (send-to-other-clients ch msg))

          :else
          (send-to-other-clients ch msg))))))




(comment
  (.getValue (audio/get-volume-control audio/*Drum1*))
  (.getMinimum (audio/get-volume-control audio/*Drum1*))
  (.getMaximum (audio/get-volume-control audio/*Drum1*))

  (.intValue (* 100  (/ 5 99)))

  )

(defn transmit-pattern-to-client [ch]
  #^{ :doc "Transmit the current sequence to the client so that it can update the step display" }
  (doseq [step         (keys @sequencer/*pattern*)]
    (let [step-events  (map #(list %1 %2)
                            (get @sequencer/*pattern* step)
                            (iterate inc 0) )]
      (doseq [[checked instrument] step-events]
        (enqueue ch
                 (json/json-str {:command "patternChange"
                                 :payload {"step"       step
                                           "instrument" instrument
                                           "checked"    checked}}
                                ))))))

(defn async-handler [ch request]
  #^{ :doc "Handler triggered when a web socket connection is established from the client"}
  (swap! *web-socket-list* conj ch)
  (receive-all ch (wsocket-receive ch))

  (if-not (some #(deref (:instrument %1)) audio/*default-instruments*)
    (audio/load-default-sounds)
    (do
      (log/info "Reconnecting to active session.  NOT reloading sounds.")
      (audio/transmit-volumes-to-client ch)))

  (let [spp-callback (fn [step]
                       (play-sounds-for-step (Integer. step))
                       (doseq [ws @*web-socket-list*]
                         (log/info "sending out spp!!!!")
                         (enqueue ws
                                  (json/json-str {:command "spp"
                                                  :payload step }))))]
    (if (or (not @sequencer/*sequencer*)
            (not (.isOpen @sequencer/*sequencer*)))
      (do
        (log/info "No open sequencer found. Creating new one.")
        (sequencer/init-sequencer spp-callback)
        (reset! sequencer/*midi-receive-spp-fn* spp-callback))
      (transmit-pattern-to-client ch))))

(defn serve-page [request]
  {:status 200
   :headers {"content-type" "text/html"}
   :body  (str-utils/str-join "\n"
                              (ds/read-lines (.getFile (wol-utils/obtain-resource-url *ns* "html/index.html")))) })

(defn serve-js [request]
  {:status 200
   :headers {"content-type" "application/x-javascript"}
   :body (str-utils/str-join "\n"
                             (ds/read-lines (.getFile (wol-utils/obtain-resource-url *ns* "javascripts/kraken.js"))))})

(def handlers
     (app
      ["javascripts"] { :get serve-js }
      ["sync"]        { :get serve-page }
      ["async"]       { :get (ahttp/wrap-aleph-handler async-handler) }))

(defn start-server []
  (log/load-log4j-file "log4j.properties")
  (reset! *server*
          (ahttp/start-http-server
           (ahttp/wrap-ring-handler handlers)
           {:port 8080 :websocket true})))

(defn close-web-sockets []
  (doseq [ws @*web-socket-list*]
    (close ws))
  (reset! *web-socket-list* []))

(defn shutdown-server []
  (@*server*)
  (reset! *server* nil)
  (close-web-sockets)
  (audio/clear-sounds)
  (if @sequencer/*sequencer*
    (.close @sequencer/*sequencer*)))

(defn restart-server []
  (shutdown-server)
  (start-server))

(comment

  (start-server)
  (shutdown-server)
  (restart-server)


  (defn detect [predicate some-seq]
    (let [hits (filter predicate some-seq)]
      (if (empty? hits)
        nil
        (first hits))))

  (defmacro detect-m
    [pred some-seq]
    `(let [hits# (filter ~pred ~some-seq)]
       (if (empty? hits#)
         nil
         (first hits#))))


  )
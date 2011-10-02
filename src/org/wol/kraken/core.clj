(ns org.wol.kraken.core
  (:gen-class)
  (:require [aleph.http                   :as  ahttp]
            [clojure.contrib.classpath    :as cp]
            [clojure.contrib.duck-streams :as ds]
            [clojure.contrib.str-utils    :as str-utils]
            [clojure.contrib.json         :as json]
            [org.wol.kraken.sequencer     :as sequencer]
            [org.wol.kraken.audio         :as audio]
            [org.wol.kraken.logging       :as log]
            [wol-utils.core               :as wol-utils]
            [ring.middleware.file-info    :as ring-file-info]
            [ring.middleware.file         :as ring-file]
            [ring.util.response          :as ring-util])
  (:use [lamina.core]
        [net.cgrand.moustache])
  (:import
   [org.apache.commons.codec.binary Base64]
   [java.nio.charset  Charset]))


(def *server* (atom nil))
(def *web-socket-list* (atom []))


(defn swank []
  (do (require 'swank.swank)
      (@(ns-resolve 'swank.swank 'start-repl)
       (Integer. 4006)
       :host "127.0.0.1"))
  (log/info "Kraken Clients's Swank should be listening on 4006"))

(defn play-sounds-for-step [step]
  (let [step-instructions (get @sequencer/*pattern* step)]
    (dotimes [i 4]
      (if (nth step-instructions i)
        (do
          (log/info (format "playing sound %d for step %d" i step))
          (audio/play-sound (nth @audio/*drums* i) ))))))

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
    (log/info (format  "Received %s from websocket" msg))
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

(defn transmit-pattern-to-client [ch]
  #^{ :doc "Transmit the current sequence to the client so that it can update the step display" }
  (log/info "transmitting pattern to client")
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

(defn transmit-drums-to-client [ch]
  #^{ :doc "Trasnmit the audio data to the client so they can play it"}
  (log/info "Transmitting audio data to client")
  (enqueue ch
           (json/json-str {:command "audioData"
                           :payload {"number"       0
                                     "data"    (audio/mine-audio-data (nth @audio/*drums* 0))}})))

(defn async-handler [ch request]
  #^{ :doc "Handler triggered when a web socket connection is established from the client"}
  (log/info "WebSocket connection. Adding new client to web-socket-list")
  (swap! *web-socket-list* conj ch)
  (receive-all ch (wsocket-receive ch))

  (if-not (some :clip  @audio/*drums*)
    (audio/load-default-sounds)
    (log/info "Reconnecting to active session.  NOT reloading sounds."))

  ;;transmit audio data
  #_(transmit-drums-to-client ch)

  ;;transmit audio volumes
  (audio/transmit-volumes-to-client ch)

  ;;set up sequencer callback
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


(def handlers
     (app
      (ring-file-info/wrap-file-info)
      (ring-file/wrap-file "public")
      ["async"]    { :get (ahttp/wrap-aleph-handler async-handler) }
      [&]          { :get (fn [request] (ring-util/redirect "html/index.html")) }))

(defn start-server []
  (wol-utils/load-log4j-file "log4j.properties")
  (log/info "starting server")
  (reset! *server*
          (ahttp/start-http-server
           (ahttp/wrap-ring-handler handlers)
           {:port 8080 :websocket true :join true})))


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

(defn -main []
  (swank)
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


  (defn secure-websocket-response-8 [request]
    (let [headers (:headers request)
          magic-string    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
          response-string (.concat (get headers (.toLowerCase "sec-websocket-key"))
                                   magic-string)
          sha-1             (.digest (MessageDigest/getInstance "SHA-1")
                                     (.getBytes response-string "UTF-8")) ]
      {:status 101
       :headers {"Sec-WebSocket-Accept"   (String. (Base64/encodeBase64 sha-1))
                 }}))


  (defn websocket-response [^HttpRequest request netty-channel options]
    (.setHeader request "content-type" "application/octet-stream")
    (let [request (transform-netty-request request netty-channel options)
          headers (:headers request)
          response (cond (and (headers "sec-websocket-key1") (headers "sec-websocket-key2"))
                         (secure-websocket-response request)

                         (= (get headers "sec-websocket-version" ) "8")
                         (secure-websocket-response-8 request)

                         :else
                         (standard-websocket-response request))]
      (def *response* response)
      (def *transformed-respone*  (transform-aleph-response
                                   (update-in response [:headers]
                                              #(assoc %
                                                 "Upgrade" "WebSocket"
                                                 "Connection" "Upgrade"))
                                   options))
      *transformed-respone*))


  )
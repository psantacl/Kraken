(ns org.wol.kraken.audio
  (:require
   [wol-utils.core :as wol-utils]
   [clojure.contrib.classpath :as cp]
   [clojure.contrib.json         :as json]
   [org.wol.kraken.logging :as log])
  (:use [lamina.core])
  (:import [javax.sound.sampled DataLine AudioSystem Clip DataLine$Info
            FloatControl$Type BooleanControl$Type AudioFormat]
           [org.apache.log4j Logger]
           [java.io ByteArrayOutputStream]))

(defn- audio-data->byte-array [ais]
  (let [frame-size (.. ais getFormat getFrameSize)
        baos        (ByteArrayOutputStream.) ]

    (loop [baos baos]
      (let [ba         (byte-array (* 1024 frame-size))
            bytes-read (.read ais ba)]
        (println bytes-read " bytes read")
        (if (= -1 bytes-read)
          (.toByteArray baos)
          (do
            (.write baos ba 0 bytes-read)
            (recur baos)))))))

(def *rosetta-audio-format* (AudioFormat. 44100.00 16 1 true false ))

(defprotocol JVMAudioClip
  "shit for java audio management"
  (get-volume-control [this])
  (get-mute-control [this])
  (clear-sound [this])
  (play-sound [this])
  (mine-audio-data [this]))


(defrecord Drum [clip audio-url]
  JVMAudioClip
  (get-volume-control [this]
                      (first (filter (fn [c] (= FloatControl$Type/MASTER_GAIN (.getType c)))
                                     (.getControls (:clip this)))))
  (get-mute-control [this]
                    (first (filter (fn [c] (= BooleanControl$Type/MUTE (.getType c)))
                                   (.getControls (:clip this)))))
  (play-sound [this]
              (.stop (:clip this))
              (.setMicrosecondPosition (:clip this) 0)
              (.start (:clip this)))
  (mine-audio-data [this]
                   (let [ais          (AudioSystem/getAudioInputStream (:audio-url this))
                         audio-format (.getFormat ais)
                         ais (if-not (.matches audio-format *rosetta-audio-format*)
                               (do
                                 (log/info (format "Converting audio clip to rosetta format %s" *rosetta-audio-format*) )
                                 (AudioSystem/getAudioInputStream *rosetta-audio-format* ais))
                               (do
                                 (log/info (format "Converting audio clip has good format %s" audio-format))
                                 ais))]
                     (audio-data->byte-array ais))))


(comment
  (clear-sounds)
  (load-default-sounds)
  (mine-audio-data (nth @*drums* 0))
  (.matches *rosetta-audio-format* (.getFormat (AudioSystem/getAudioInputStream (:audio-url (nth @*drums* 2)))))
  )


(def *max-volume* 13.9794)
(def *min-volume* -40)


(def *drums* (ref []))

(def *default-instruments*
     [ "sounds/tonebell01.aif"  "sounds/clickthump1.wav"  "sounds/blue.wav"  "sounds/click2.wav"])


(defn clear-sounds []
  (log/info "Clearing sounds")
  (dosync
   (ref-set *drums* [])))


(defn load-sound [audio-url]
  (let [clip   (with-open [ais (AudioSystem/getAudioInputStream audio-url) ]
                 (let [line-info (DataLine$Info. Clip (.getFormat ais))
                       clip       (AudioSystem/getLine line-info)]
                   (.open clip ais)
                   clip))
        nascent-drum (Drum. clip audio-url )]
    (.setValue (get-volume-control nascent-drum) *max-volume*)
    (dosync
     (alter *drums* conj nascent-drum))))

(defn load-default-sounds []
  (doseq [audio-file *default-instruments*]
    (let [audio-url  (wol-utils/obtain-resource-url *ns* audio-file)]
      (load-sound audio-url))))


;; (defn load-sound-from-resource [instrument resource]
;; (let [audio-url (wol-utils/obtain-resource-url
;;                  *ns*
;;                  resource)]
;;   (load-sound-from-url instrument audio-url)))


(comment
  ;;;lambda
  (.getControls (:clip (first @*drums*)))
  (.getLineInfo (:clip (first @*drums*)))
  (.getMicrosecondLength (:clip (first @*drums*)))
  (.setMicrosecondPosition (:clip (first @*drums*)) 50000)

  (play-sound (first @*drums*))
  (.getMinimum (get-volume-control (first @*drums*)))
  (.getMaximum (get-volume-control (first @*drums*)))

  (.setValue (get-volume-control (first @*drums*)) 0.0)
    (.setValue (get-volume-control (first @*drums*)) 13)
  )

(defn volume-change [{instr-num :instrument new-volume :value}]
  (let [drum        (nth @*drums* instr-num)
        vol-control (get-volume-control drum)
        new-value   (+ (* (/ new-volume 100)
                          (- *max-volume* *min-volume*))
                       *min-volume*)]
    (log/info (format "Setting volume for instrument %s to %f" instr-num new-value))
    (.setValue vol-control new-value )))

(defn transmit-volumes-to-client [ch]
  #^{ :doc "Transmit the current volume settings to the client so that it can update the volume sliders" }
  (log/info "Resyncing volume controls.")
  (doseq [[i instr] (map (fn [instr i] [instr i])
                                       (range (count @*drums* ))
                                       @*drums*)]
    (let [vol-control (get-volume-control instr)
          vol         (.getValue vol-control)]
      (log/info (format "Sending volume %d"
                        (.intValue (* 100 (/ (- vol *min-volume*)
                                             (- *max-volume* *min-volume*)
                                             )))))
      (enqueue ch
               (json/json-str
                {:command "volume"
                 :payload {"instrument" i
                           "value" (.intValue (* 100 (/ (- vol *min-volume*)
                                                        (- *max-volume* *min-volume*)
                                                        )))}
                 })))))


(comment
  (count @*drums*)
  (load-default-sounds)
  (play-sound (nth @*drums* 1))


  (get-volume-control (nth @*drums* 1))



  (/  (.getMicrosecondLength (deref (:clip *Drum1*))) 1000.0)

  (.isActive (deref (:clip *Drum1*)))
  (.isRunning (deref (:clip  *Drum1*)))
  (.setMicrosecondPosition (deref (:clip *Drum1*)) 0)
  (.getMicrosecondPosition  (deref (:clip *Drum1*)))
  (.getMicrosecondLength (deref (:clip *Drum1*)))
  (.getBufferSize (deref (:clip *Drum1*)))
  (.getLineInfo (deref (:clip *Drum1*)))
  (.getControls (deref (:clip *Drum1*)))
  (.getBufferSize (deref (:clip *Drum1*)))

  (.getLevel (deref (:clip *Drum1*)))
  (.getLongFramePosition (deref (:clip *Drum1*)))


  )

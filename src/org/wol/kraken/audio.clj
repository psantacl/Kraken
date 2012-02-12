(ns org.wol.kraken.audio
  (:require
   [wol-utils.core               :as wol-utils]
   [clojure.contrib.classpath    :as cp]
   [clojure.contrib.json         :as json]
   [clj-etl-utils.log            :as log])
  (:use
   [wol.websockets.server :only [*web-socket* ws-respond]])
  (:import [javax.sound.sampled DataLine AudioSystem Clip DataLine$Info
            FloatControl$Type BooleanControl$Type AudioFormat]
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
  (close-clip [this])
  (mine-audio-data [this]))

(defrecord Drum [clip audio-file]
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
  (close-clip [this]
         (.close (:clip this)))
  (mine-audio-data [this]
                   (let [ais          (AudioSystem/getAudioInputStream (:audio-file this))
                         audio-format (.getFormat ais)
                         ais (if-not (.matches audio-format *rosetta-audio-format*)
                               (do
                                 (log/infof "Converting audio clip to rosetta format %s" *rosetta-audio-format* )
                                 (AudioSystem/getAudioInputStream *rosetta-audio-format* ais))
                               (do
                                 (log/infof "Converting audio clip has good format %s" audio-format)
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


(defn clear-sounds []
  (log/infof "Clearing sounds")
  (dosync
   (ref-set *drums* [])))

(defn load-sound [audio-file]
  (let [clip   (with-open [ais (AudioSystem/getAudioInputStream audio-file) ]
                 (let [line-info (DataLine$Info. Clip (.getFormat ais))
                       clip       (AudioSystem/getLine line-info)]
                   (.open clip ais)
                   clip))
        nascent-drum (Drum. clip audio-file )]
    (.setValue (get-volume-control nascent-drum) *max-volume*)
    (dosync
     (alter *drums* conj nascent-drum))))

(defn replace-sound [audio-file position]
  (let [clip   (with-open [ais (AudioSystem/getAudioInputStream audio-file) ]
                 (let [line-info (DataLine$Info. Clip (.getFormat ais))
                       clip       (AudioSystem/getLine line-info)]
                   (.open clip ais)
                   clip))
        nascent-drum (Drum. clip audio-file )]
    (.setValue (get-volume-control nascent-drum) *max-volume*)
    (let [old-drum (nth @*drums* position) ]
     (dosync
      (alter *drums*
             (fn [drums]
               (concat (take position drums)
                       [nascent-drum]
                       (drop (inc position) drums)))))
     (close-clip old-drum))))



(comment
  (replace-sound (java.io.File. "drums/vermonasnare1.wav") 3)
  (replace-sound (java.io.File. "drums/vermonasnare1.wav") 0)

  (play-sound (last @*drums*))
  (play-sound (first  @*drums*))


  )

(defn get-drum-list []
  (let [drum-files (filter #(.isFile %) (file-seq (java.io.File. "./drums/")))]
    drum-files))

(defn load-default-sounds []
  (doseq [drum (take 4 (get-drum-list))]
    (load-sound drum)))




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
    (log/infof "Setting volume for instrument %s to %f" instr-num new-value)
    (.setValue vol-control new-value )))

(defn transmit-volumes-to-client []
  #^{ :doc "Transmit the current volume settings to the client so that it can update the volume sliders" }
  (log/infof "Resyncing volume controls.")
  (doseq [[i instr] (map (fn [instr i] [instr i])
                         (range (count @*drums* ))
                         @*drums*)]
    (let [vol-control (get-volume-control instr)
          vol         (.getValue vol-control)]
      (log/infof "Sending volume %d"
                 (.intValue (* 100 (/ (- vol *min-volume*)
                                      (- *max-volume* *min-volume*)
                                      ))))
      (ws-respond
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

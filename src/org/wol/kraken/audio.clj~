(ns org.wol.kraken.audio
  (:require
   [wol-utils.core :as wol-utils]
   [clojure.contrib.classpath :as cp]
   [clojure.contrib.json         :as json]
   [org.wol.kraken.logging :as log])
  (:use [lamina.core])
  (:import [javax.sound.sampled DataLine AudioSystem Clip DataLine$Info
            FloatControl$Type BooleanControl$Type]
           [org.apache.log4j Logger]))

(def *Drum1* (atom nil))
(def *Drum2* (atom nil))
(def *Drum3* (atom nil))
(def *Drum4* (atom nil))

(def *max-volume* 13.9794)
(def *min-volume* -40)

(defn get-volume-control [instrument]
  (first (filter (fn [c] (= FloatControl$Type/MASTER_GAIN (.getType c)))
                 (.getControls @instrument))))

(defn get-mute-control [instrument]
  (first (filter (fn [c] (= BooleanControl$Type/MUTE (.getType c)))
               (.getControls @instrument))))

(defn load-sound-from-url [instrument audio-url]
  (let [ain (AudioSystem/getAudioInputStream audio-url)]
   (try
    (let [line-info (DataLine$Info. Clip (.getFormat ain))
          clip (AudioSystem/getLine line-info)]
      (reset! instrument clip)
      (.open @instrument ain))
    (finally
     (.close ain)))))

(def *default-instruments*
     [ {:instrument *Drum1* :file "sounds/tonebell01.aif"}
       {:instrument *Drum2* :file "sounds/clickthump1.wav"}
       {:instrument *Drum3* :file "sounds/blue.wav"}
       {:instrument *Drum4* :file "sounds/click2.wav"} ])

(defn clear-sounds []
  (log/info "Clearing sounds")
  (doseq [{instr :instrument file :file} *default-instruments* ]
    (reset! instr nil)))

(defn load-default-sounds []
  (doseq [{instr :instrument file :file} *default-instruments* ]
    (let [audio-url (wol-utils/obtain-resource-url
                     *ns*
                     file)]
      (load-sound-from-url instr audio-url)
      (.setValue (get-volume-control instr) *max-volume*))))

(defn load-sound-from-resource [instrument resource]
   (let [audio-url (wol-utils/obtain-resource-url
                     *ns*
                     resource)]
      (load-sound-from-url instrument audio-url)))


(comment
  (.setValue (get-mute-control *Drum3*) true)
  ;;;-80...13.9
  (.setValue  (get-volume-control *Drum1*) 8.0)
  (.setValue  (get-volume-control *Drum2*)   13.9794)
  (.setValue  (get-volume-control *Drum3*) -5.0)
  (.setValue  (get-volume-control *Drum4*) 7.0)

  (.getValue  (get-volume-control *Drum1*))
  (.getValue  (get-volume-control *Drum2*))
  (.getValue  (get-volume-control *Drum3*))
  (.getValue  (get-volume-control *Drum4*))

  (load-sound-from-resource *Drum1* "sounds/blue.wav")
  (load-sound-from-resource *Drum2* "sounds/click2.wav")
  (load-sound-from-resource *Drum1* "sounds/tonebell01.aif")
  (load-sound-from-resource *Drum2* "sounds/clickthump1.wav")
  (load-sound-from-resource *Drum1* "sounds/dit.wav")
  (load-sound-from-resource *Drum2* "sounds/donk.wav")
  (load-sound-from-resource *Drum3* "sounds/vermonasnare1.wav")
  (load-sound-from-resource *Drum3* "sounds/red.wav")
  (load-sound-from-resource *Drum3* "sounds/sickofnoisesnaresyet.aif")


  (AudioSystem/getAudioInputStream
   (-> (.getClass *ns*)
       (.getClassLoader)
       (.getResourceAsStream "blue.wav")))

  )

(defn play-sound [instr-num]
  (let [var-sym (symbol (format "org.wol.kraken.audio/*Drum%d*" instr-num))
        instr-var (find-var var-sym)
        instr-atom (deref instr-var)]
    (.start @instr-atom)))

(defn play-sound-from-atom [snd-atom]
  (.stop @snd-atom)
  (.setMicrosecondPosition @snd-atom 0)
  (.start @snd-atom))


(defn volume-change [{instr-num :instrument new-volume :value}]
  (let [instr (:instrument (nth *default-instruments* instr-num))
        vol-control (get-volume-control instr)
        new-value   (+ (* (/ new-volume 100)
                          (- *max-volume* *min-volume*))
                       *min-volume*)]
    (log/info (format "Setting volume for instrument %s to %f" instr-num new-value))
    (.setValue vol-control new-value )))

(defn transmit-volumes-to-client [ch]
  #^{ :doc "Transmit the current volume settings to the client so that it can update the volume sliders" }
  (log/info "Resyncing volume controls.")
  (doseq [[i {instr :instrument}] (map (fn [instr i] [instr i])
                                        (range (count *default-instruments* ))
                                        *default-instruments*)]
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
  (play-sound-from-atom *Drum1*)
  (play-sound-from-atom *Drum2*)
  (play-sound-from-atom *Drum3*)
  (play-sound-from-atom *Drum4*)

  (/  (.getMicrosecondLength @*Drum1*) 1000.0)

  (.start @*Drum1*)
  (.stop @*Drum1*)
  (.isActive @*Drum1*)
  (.isRunning @*Drum1*)
  (.setMicrosecondPosition @*Drum1* 0)
  (.getMicrosecondPosition  @*Drum1*)
  (.getMicrosecondLength @*Drum1*)
  (.getBufferSize @*Drum1*)
  (.getLineInfo @*Drum1*)
  (.getControls @*Drum1*)
  (.getBufferSize @*Drum1*)

  (.getLevel @*Drum1*)
  (.getLongFramePosition @*Drum1*)


  )

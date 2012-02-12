(ns org.wol.kraken.clients
  (:require
   [clojure.contrib.json         :as json]
   [org.wol.kraken.audio        :as audio])
  (:use [wol.websockets.server  :only [ws-respond]]))

(defn item-position [col pred-fn]
  (loop [[item & items] col
         idx 0]
    (if (nil? item)
      nil
      (if (pred-fn item)
        idx
        (recur items (inc idx))))))

(defn detect [predicate some-seq]
  (let [hits (filter predicate some-seq)]
    (if (empty? hits)
      nil
      (first hits))))

(defn transmit-drum-list []
  (let [drums (map #(.getName %) (audio/get-drum-list))
        payload (reduce (fn [accum drum-file]
                          (conj accum {:name drum-file
                                       :clip (item-position @audio/*drums*
                                                            (fn [drum] (= drum-file (.getName (:audio-file drum))) ))}))
                        []
                        drums)]
    (ws-respond
             (json/json-str {:command "drumList"
                             :payload
                             {"value"  payload}}))))

(comment


  (transmit-drum-list nil)
  [{:name "blue.wav", :clip 0} {:name "click2.wav", :clip 1} {:name "clickthump1.wav", :clip 2} {:name "dit.wav", :clip 3} {:name "donk.wav", :clip nil} {:name "red.wav", :clip nil} {:name "sickofnoisesnaresyet.aif", :clip nil} {:name "tonebell01.aif", :clip nil} {:name "vermonasnare1.wav", :clip nil}]
  (item-position @audio/*drums*
                 (fn [item] (= "clickthump1.wav" (.getName (:audio-file item))) ))



  (detect #(= (.getName %) "drums") (file-seq (java.io.File. ".")))



  )
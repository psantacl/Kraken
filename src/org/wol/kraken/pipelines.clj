(ns org.wol.kraken.pipelines
  (:require [clojure.contrib.duck-streams :as ds]
            [clojure.contrib.io :as io]
            [clojure.contrib.shell-out :as sh])
  (:use
   [lamina.core]))

;;;regular synchronous pipeline
(let [ch (channel)
      our-future (future
                   (Thread/sleep 10000)
                   (enqueue ch "chickens "))]
  (run-pipeline ch
                read-channel
                (fn [value] (.concat value " are the best!"))
                (fn [value] (.toUpperCase value )))
  (println "this thread is still processing!"))

;;;asynchronous pipeline
(def *out-channel* (channel))
(let [in-ch (channel)
      our-future (future
                   (Thread/sleep 10000)
                   (enqueue in-ch "chickens "))]
  (run-pipeline in-ch
                read-channel
                (fn [value] (.concat value " are the best!"))
                (fn [value] (.toUpperCase value ))
                #(enqueue *out-channel* %)))


;;;let's do a shootout
(let [in-ch (channel)
      out-ch (channel)
      start-time  (System/nanoTime)
      repititions   100]
  (receive-all out-ch (fn [msg]
                        (when (= msg "done")
                          (println "done: " (/ (double (- (System/nanoTime) start-time))
                                               1000000.0)))))
  (dotimes [i repititions]
    (future
      (enqueue in-ch
               (sh/sh "dig" "www.relaynetwork.com")))
    (run-pipeline in-ch
                  read-channel
                  (fn [value] (.toUpperCase value ))
                  (fn [value]
                    (enqueue out-ch value)
                    (if (= i (- repititions 1))
                      (enqueue out-ch "done"))))
    (.println *out* (str "this thread is still processing!" i))
    (.flush *out*)))

;;;execution in the main thread continues after the asychronous shell out.  Once a pipeline has completed
;;;the on-success fn is triggered.
(let [in-ch (channel)
      out-ch (channel)
      start-time  (System/nanoTime)
      repititions   50
      our-pipeline   (pipeline
                      read-channel
                      (fn [value] (.toUpperCase value ))
                      (fn [value]
                        (enqueue out-ch value)))]

  (dotimes [i repititions]
    (future
      (enqueue in-ch
               #_(sh/sh "dig" "www.relaynetwork.com")
               (sh/sh "ls")))
    (on-success (our-pipeline in-ch) (fn [result]
                                       (.println *out* (str "finished a pipeline: " result))
                                       (.flush *out*)))
    (.println *out* (str "this thread is still processing: " i))
    (.flush *out*)))

(time (let [out-ch (channel)
            repititions   100]
        (receive-all out-ch (fn [msg]
                              (println msg)))
        (dotimes [i repititions]
          (run-pipeline nil
                        (fn [value] (sh/sh "dig" "www.relaynetwork.com"))
                        (fn [value] (.toUpperCase value ))
                        (fn [value]
                          (enqueue out-ch value)
                          (if (= i (- repititions 1))
                            (enqueue out-ch "done")))))))



(comment

  (.println *out* "sdfdsfd")

  )








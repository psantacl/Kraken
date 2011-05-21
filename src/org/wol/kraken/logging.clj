(ns org.wol.kraken.logging
  (:require [clojure.string               :as string]
            [wol-utils.core               :as wol-utils])
  (:import [org.apache.log4j PropertyConfigurator Logger]))

(def *logger* (Logger/getRootLogger))
(def *file-logger* (Logger/getLogger "org.wol.kraken.core"))

(defn info [& msg]
  (.info *logger* (clojure.string/join " " msg)))

(defn error [& msg]
  (.error *logger* (clojure.string/join " " msg)))

(defn load-log4j-file [resource-uri]
  (let [log4j-conf (java.io.FileInputStream.
                    (.getFile (wol-utils/obtain-resource-url *ns* "log4j.properties")))
        p (doto (java.util.Properties.)
            (.load log4j-conf))]
    (PropertyConfigurator/configure p)))

(comment
  (.debug *file-logger* "here we go")
  (.debug *logger* "you won't see this")
  (.info *logger* "but you will see this!")
  (info "check this out " 44)
  (error "someone fucked up")


  (-> (Logger/getRootLogger) .getLevel)
  (-> (Logger/getLogger "org.wol.kraken.core") .getLevel)
  (log/enabled? :info)

  )
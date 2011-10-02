#!/bin/bash

set -e
set -x

java -cp $(dirname $0)/../kraken-1.0.0-SNAPSHOT-standalone.jar clojure.main -e "(require 'org.wol.kraken.core) (org.wol.kraken.core/-main)"



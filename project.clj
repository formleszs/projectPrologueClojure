(defproject maze-game "0.1.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [org.clojure/data.json "2.4.0"]
                 [seancorfield/next.jdbc "1.3.874"]
                 [com.layerware/hugsql "0.5.3"]
                 [tupelo "23.08.09"]
                 [org.slf4j/slf4j-simple "2.0.7"]]
  :main maze-game.core
  :profiles {:dev {:dependencies [[nrepl "1.0.0"]]}})
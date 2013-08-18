(defproject ifesdjeen.clj-pusher "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [clojurewerkz/eep "0.5.2"]
                 [aleph "0.3.0-rc2"]
                 [ring/ring "1.2.0"]
                 [ring-reload-modified "0.1.1"]
                 [compojure "1.1.5"]
                 [cheshire "5.0.2"]
                 [de.ubercode.clostache/clostache "1.3.1"]

                 [org.clojure/tools.nrepl "0.2.2"]

                ]
  :source-paths   ["src"]
  :resource-paths ["src-templates"]
  :main ifesdjeen.clj-pusher.core
  :jvm-opts ["-server"
             "-XX:MaxPermSize=512m"
             "-Dfile.encoding=utf-8"
             "-XX:+UseConcMarkSweepGC"
             "-XX:+CMSClassUnloadingEnabled"
             "-XX:+UseStringCache"
             "-XX:+OptimizeStringConcat"
             "-XX:+UseCompressedOops"
             "-XX:+DoEscapeAnalysis"]
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true :releases {:checksum :fail :update :always}}})

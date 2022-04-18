(defproject herfi "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.10"]
                 [clojure.java-time "0.3.3"]
                 [cprop "0.1.19"]
                 [expound "0.9.0"]
                 [luminus-aleph "0.1.6"]
                 [luminus-transit "0.1.5"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.8"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.15"]
                 [metosin/ring-http-response "0.9.3"]
                 [mount "0.1.16"]
                 [nrepl "0.9.0"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.5.648"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [selmer "1.12.50"]
                 [clojure-msgpack "1.2.1"]
                 [jarohen/chime "0.3.3"]
                 [amalloy/ring-gzip-middleware "0.1.4"]
                 [ring-cors/ring-cors "0.1.13"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot herfi.core

  :plugins []

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "herfi.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:jvm-opts ["-Dconf=dev-config.edn"]
                 :dependencies [[org.clojure/tools.namespace "1.2.0"]
                                [pjstadig/humane-test-output "0.11.0"]
                                [prone "2021-04-23"]
                                [ring/ring-devel "1.9.5"]
                                [ring/ring-mock "0.4.0"]]
                 :plugins [[com.jakemccrary/lein-test-refresh "0.24.1"]
                           [jonase/eastwood "0.3.5"]
                           [cider/cider-nrepl "0.26.0"]]

                 :source-paths ["env/dev/clj"]
                 :resource-paths ["env/dev/resources"]
                 :repl-options {:init-ns user
                                :init (start)
                                :timeout 120000}
                 :injections [(require 'pjstadig.humane-test-output)
                              (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})

(defproject clj-gamification "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [http-kit "2.1.4"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.3"]
                 [hiccup-bootstrap "0.1.2"]
                 [org.clojure/data.json "0.2.2"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler clj-gamification.handler/app, :auto-refresh? true, :port 5000, :nrepl {:start? true, :port 4555}}
  :main clj-gamification.handler
  :profiles
  {:dev {:source-paths ["dev"],
         :dependencies [[ring-mock "0.1.5"]]},
   :production {:misc "configuration", ; app-specific stuff
                :mirrors {"central" "http://s3pository.herokuapp.com/clojure"}}}
  :min-lein-version "2.0.0") ;; ring-serve has old hiccup

(defproject clj-gamification "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.3"]
                 [hiccup-bootstrap "0.1.2"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler clj-gamification.handler/app, :auto-refresh? true, :nrepl {:start? true #_(:port 7000)}}
  :profiles
  {:dev {:source-paths ["dev"],
         :dependencies [[ring-mock "0.1.5"]]},
   :production {:misc "configuration", ; app-specific stuff
                :mirrors {"central" "http://s3pository.herokuapp.com/clojure"}}}
  :min-lein-version "2.0.0") ;; ring-serve has old hiccup

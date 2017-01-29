(set-env!
 :source-paths #{"src" "test"}
 :resource-paths #{"resources"}

 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]

                 ; boot
                 [adzerk/boot-cljs "1.7.228-1"]
                 [pandeiro/boot-http "0.7.3"]
                 [adzerk/boot-cljs-repl "0.3.3"]
                 [tolitius/boot-check "0.1.3"]
                 [adzerk/boot-test "1.1.2" :scope "test"]

                 ; for boot-cljs-repl
                 [com.cemerick/piggieback "0.2.1"]
                 [weasel "0.7.0"]
                 [org.clojure/tools.nrepl "0.2.12"]

                 ; project dependencies
                 [http-kit "2.2.0"]
                 [ring "1.5.0"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [compojure "1.5.1"]
                 [environ "1.1.0"]
                 [reagent "0.6.0"]
                 [com.taoensso/sente "1.11.0"]
                 [org.clojure/core.async "0.2.395"]
                 [instaparse "1.4.5"]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[tolitius.boot-check :as check]
         '[adzerk.boot-test :refer :all]
         'checkers.server)

(deftask build []
  (comp
   (cljs :optimizations :advanced)
   (aot :namespace '#{checkers.server})
   (pom :project 'checkers
        :version "1.0.0-SNAPSHOT"
        :description "Checkers demo"
        :license {"GNU AGPLv3" "http://www.gnu.org/licenses/agpl-3.0-standalone.html"}
        :developers {"J-P Heini" "jajuhein@gmail.com"})
   (uber)
   (jar :file "app.jar" :main 'checkers.server)
   (sift :include #{#"app.jar"})
   (target)))

(deftask dev []
  (comp
   (with-pre-wrap fileset
     (checkers.server/-main)
     fileset)
   (watch) ; without watch boot will exit after the last task
   (cljs-repl)
   (cljs :source-map true :optimizations :none)
   (target)))

(deftask check-sources []
  (comp
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))

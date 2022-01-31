(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/solution_3.jar"))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["."]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'sieve}))
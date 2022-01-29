#!/bin/sh

clojure -M -e "(compile 'sieve)"
clojure -M:uberjar --main-class sieve

java -jar target/solution_3.jar :variant :boolean-array :warm-up? true

#clojure -X sieve/run :variant :vector :warm-up? false
#clojure -X sieve/run :variant :vector-transient :warm-up? false
#clojure -X sieve/run :variant :bitset :warm-up? true
#clojure -X sieve/run :variant :boolean-array :warm-up? true

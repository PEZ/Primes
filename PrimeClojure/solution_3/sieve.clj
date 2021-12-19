(ns sieve
  "Clojure implementations of The Sieve of Eratosthenes by Peter Strömberg (a.k.a. PEZ)"
  (:require [criterium.core :refer [with-progress-reporting bench quick-bench]])
  (:import [java.time Instant Duration]))

;; Disable overflow checks on mathematical ops and warn when compiler is unable
;; to optimise correctly.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn sieve-ba-post-even-filter
  "boolean-array-storage
   Returns the primes.
   We gain some time by skipping every other number while iterating.
   Then lose some time when filtering away the even numbers that we missed.
   The second step can be parallalized for some little speed gain.
   It all starts to make sense for larger sieves,
     at 1 million the gains are there, but small."
  [^long n]
  (if (< n 2)
    []
    (let [primes (boolean-array (inc n) true)
          sqrt-n (int (Math/ceil (Math/sqrt n)))]
      (loop [p 3]
        (if (< sqrt-n p)
          (let [num-slices 8 ; I'm not sure what is a good default
                num-slices (if (and (zero? ^long (mod n num-slices))
                                    (> n 1000))
                             num-slices
                             1)
                slice-size (quot n num-slices)
                futures (mapv (fn [^long slice-num]
                                (future
                                  (let [start (inc (* slice-num slice-size))
                                        end (dec (+ start slice-size))
                                        start (if (= start 1) 3 start)]
                                    (loop [res (transient [])
                                           i start]
                                      (if (<= i end)
                                        (recur (if (aget primes i)
                                                 (conj! res i)
                                                 res)
                                               (+ i 2))
                                        (persistent! res))))))
                              (range num-slices))]
            (into [2]
                  (mapcat deref)
                  futures))
          (do
            (when (aget primes p)
              (loop [i (* p p)]
                (when (<= i n)
                  (aset primes i false)
                  (recur (+ i p p)))))
            (recur  (+ p 2))))))))

(comment
  (sieve-ba-post-even-filter 1)
  ;; => []

  (sieve-ba-post-even-filter 10)
  ;; => [2 3 5 7]

  (sieve-ba-post-even-filter 100)
  ;; You try it!

  (with-progress-reporting (quick-bench (sieve-ba-post-even-filter 1000000)))
  (quick-bench (sieve-ba-post-even-filter 1000000))
  ;; Execution time mean : 2.703046 ms

  ;; This one takes a lot of time, you have been warned
  (with-progress-reporting (bench (sieve-ba-post-even-filter 1000000)))
  )

(defn sieve-ba-pre-even-filter
  "boolean-array storage
   Returns the raw sieve.
   Remove even indexes before sieving.
   No parallelisation."
  [^long n]
  (if (< n 2)
    (boolean-array (inc n))
    (let [primes (boolean-array (inc n) true)
          sqrt-n (long (Math/ceil (Math/sqrt n)))]
      (aset primes 0 false)
      (aset primes 1 false)
      (loop [i 4]
        (when (<= i n)
          (aset primes i false)
          (recur (+ i 2))))
      (loop [p 3]
        (if-not (< p sqrt-n)
          primes
          (do
            (loop [i (* p p)]
              (when (<= i n)
                (aset primes i false)
                (recur (+ i p p))))
            (recur (+ p 2))))))))

(comment
  ;; We get the raw sieve back, a Java boolean array
  (sieve-ba-pre-even-filter 1)
  ;; => #object ["[Z" 0x816eab0 "[Z@816eab0"]

  ;; Most often you can treat it as a Clojure sequence/collection
  (first (sieve-ba-pre-even-filter 1))
  ;; => false

  (nth (sieve-ba-pre-even-filter 3) 2)
  ;; => true

  ;; Evaluate this one to use for emptying/looting the raw sieve
  (defn loot [raw-sieve]
    (keep-indexed (fn [i v] (when v i)) raw-sieve))

  (loot (sieve-ba-pre-even-filter 1))
  ;; => ()

  (loot (sieve-ba-pre-even-filter 10))
  ;; => (2 3 5 7)

  (-> 1000000
      sieve-ba-pre-even-filter
      loot 
      count)
  ;; => 78498

  (loot (sieve-ba-pre-even-filter 100))
  ;; You try it!

  (with-progress-reporting (quick-bench (sieve-ba-pre-even-filter 1000000)))
  (quick-bench (sieve-ba-pre-even-filter 1000000))
  ;; Execution time mean : 1.563407 ms

  ;; This one takes a lot of time, you have been warned
  (with-progress-reporting (bench (sieve-ba-pre-even-filter 1000000)))
  )

(defn sieve-bs-pre-even-filter
  "BitSet storage.
   Returns the raw sieve.
   Remove even indexes before sieving.
   No parallelisation."
  [^long n]
  (if (< n 2)
    (java.util.BitSet. n)
    (let [primes (doto (java.util.BitSet. n) (.set 2 (inc n)))
          sqrt-n (long (Math/ceil (Math/sqrt n)))]
      (loop [i 4]
        (when (<= i n)
          (.clear primes i)
          (recur (+ i 2))))
      (loop [p 3]
        (if-not (< p sqrt-n)
          primes
          (do
            (loop [i (* p p)]
              (when (<= i n)
                (.clear primes i)
                (recur (+ i p p))))
            (recur (+ p 2))))))))

(comment
  ;; We get the raw sieve back, a Java BitSet
  (sieve-bs-pre-even-filter 1)
  ;; => #object [java.util.BitSet 0x222b4705 "{}"]

  (sieve-bs-pre-even-filter 10)
  ;; => #object [java.util.BitSet 0x5793c7ae "{2, 3, 5, 7}"]

  (.cardinality (sieve-bs-pre-even-filter 1000000))
  ;; => 78498

  (sieve-bs-pre-even-filter 100)
  ;; You try it!

  (with-progress-reporting (quick-bench (sieve-bs-pre-even-filter 1000000)))
  (quick-bench (sieve-bs-pre-even-filter 1000000))
  ;; Execution time mean : 5.173709 ms

  ;; This one takes a lot of time, you have been warned
  (with-progress-reporting (bench (sieve-bs-pre-even-filter 1000000)))
  )

(def prev-results
  "Previous results to check against sieve results."
  {1           0
   10          4
   100         25
   1000        168
   10000       1229
   100000      9592
   1000000     78498
   10000000    664579
   100000000   5761455
   1000000000  50847534
   10000000000 455052511})


(defn benchmark
  "Benchmark Sieve of Eratosthenes algorithm."
  [sieve count-f]
  (let [limit       1000000
        start-time  (Instant/now)
        end-by      (+ (.toEpochMilli start-time) 5000)]
    (loop [pass 1]
      (let [primes   (sieve limit)
            cur-time (System/currentTimeMillis)]
        (if (<= cur-time end-by)
          (recur (inc pass))
          ;; Return benchmark report.
          {:primes primes
           :passes pass
           :limit  limit
           :time   (Duration/between start-time (Instant/now))
           :valid? (= (count-f primes)
                      (prev-results limit))})))))


;; Reenable overflow checks on mathematical ops and turn off warnings.
(set! *warn-on-reflection* false)
(set! *unchecked-math* false)


(defn format-results
  "Format benchmark results into expected output."
  [{:keys [primes passes limit time valid? variant count-f threads bits]}]
  (let [nanos (.toString (.toNanos time))
        timef (str (subs nanos 0 1) "." (subs nanos 1))]
    (str "Passes: " passes ", "
         "Time: " timef ", "
         "Avg: " (float (/ (/ (.toNanos time) 1000000000) passes)) ", "
         "Limit: " limit ", "
         "Count: " (count-f primes) ", "
         "Valid: " (if valid? "True" "False")
         "\n"
         "pez-clj-" (name variant) ";" passes ";" timef ";" threads ";algorithm=base,faithful=yes,bits=" bits)))

(def confs
  {:boolean-array-futures {:sieve sieve-ba-post-even-filter
                           :count-f count
                           :threads 8
                           :bits 8}
   :boolean-array {:sieve sieve-ba-pre-even-filter
                   :count-f (fn [primes] (count (filter true? primes)))
                   :threads 1
                   :bits 8}
   :bitset {:sieve sieve-bs-pre-even-filter
            :count-f (fn [primes] (.cardinality primes))
            :threads 1
            :bits 1}})

(defn run [{:keys [variant warm-up?]
            :or   {variant :boolean-array
                   warm-up? false}}]
  (let [conf (confs variant)
        sieve (:sieve conf)
        count-f (:count-f conf)]
    (when warm-up?
    ;; Warm-up reduces the variability of results.
      (format-results (merge conf (benchmark sieve count-f) {:variant variant})))
    (println (format-results (merge conf (benchmark sieve count-f) {:variant variant})))))

(comment
  (run {:warm-up? false})
  (run {:variant :boolean-array-futures :warm-up? false})
  (run {:variant :bitset :warm-up? false})
  (run {:warm-up? true})
  )
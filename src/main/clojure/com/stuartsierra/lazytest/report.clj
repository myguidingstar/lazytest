(ns com.stuartsierra.lazytest.report
  (:use [com.stuartsierra.lazytest.results
	 :only (success? container? error? pending?)]
        [com.stuartsierra.lazytest.color :only (colorize)]
        [clojure.stacktrace :only (print-cause-trace)])
  (:import (java.io File)))

(defn details
  "Given a TestResult, returns the map of :name, :ns, :file, :line,
  :generator, and :form."
  [r]
  (meta (:source r)))

(defn print-details
  "Prints full details of a TestResult, including file and line
  number, doc string, and stack trace if applicable."
  [r]
  (println
   (cond (pending? r) (colorize "PENDING" :fg-yellow)
         (success? r) (colorize "SUCCESS" :fg-green)
         (error? r) (colorize "ERROR" :fg-red)
         :else (colorize "FAIL" :fg-red)))
  (let [m (details r)]
    (when-let [n (:name m)] (println "Name:" n))
    (when-let [nn (:ns m)] (println "NS:  " (ns-name nn)))
    (when-let [d (:doc m)] (println "Doc: " d))
    (when (and (:form m) (not (:name m)))
      (print "Form: ")
      (prn (:form m)))
    (when-let [f (:file m)] (println "File:" f))
    (when-let [l (:line m)] (println "Line:" l))
    (when (seq (:locals m))
      (print "Givens: ")
      (prn (zipmap (:locals m) (:states r))))
    (when-let [e (:throwable r)]
      (println "STACK TRACE")
      (print-cause-trace e))))

(defn assertion-results
  "Returns a sequence of all assertion results (not grouped test
  results) in the tree rooted at r."
  [r]
  r)

(defn- inc-keys
  "Increment the value of each key in map m."
  [m & keys]
  (reduce (fn [m k] (assoc m k (inc (get m k 0))))
          m keys))

(defn summary
  "Returns a map of :total, :pass, :fail, and :error
  counts for assertions in the results tree rooted at r."
  [res]
  (reduce (fn [m r]
            (cond (pending? r) (inc-keys m :pending)
                  (container? r) m
                  (success? r) (inc-keys m :assertions :pass)
                  (error? r) (inc-keys m :assertions :error)
                  :else (inc-keys m :assertions :fail)))
          {:assertions 0, :pass 0, :fail 0, :error 0, :pending 0}
          res))

(defn print-summary [r]
  (let [{:keys [assertions pass fail error pending]} (summary r)]
    (println (colorize (str "Ran " assertions " assertions.")
                       (if (zero? assertions) :fg-yellow :reset)))
    (print (colorize (str fail " failures")
                     (if (zero? fail) :fg-green :fg-red)))
    (print ", ")
    (print (colorize (str error " errors")
                     (if (zero? error) :fg-green :fg-red)))
    (print ", ")
    (print (colorize (str pending " pending")
                     (if (zero? pending) :fg-green :fg-yellow)))
    (newline)
    (flush)))

(defn- spec-report* [r parents]
  (if (and (container? r) (not (pending? r)))
    (doseq [c (:children r)]
      (spec-report* c (conj parents r)))
    (if (and (success? r) (not (pending? r)))
      (do (print (colorize "." :fg-green)) (flush))
      (do (newline)
          (print-details
           (assoc r :source
                  (vary-meta (:source r) assoc :doc
                             (apply str (interpose " "
                                                   (filter identity
                                                           (map #(:doc (details %))
                                                                (conj parents r))))))))))))

(defn spec-report
  "Simple report of spec results.  Prints a dot for each passed
  assertion; prints details for each failure or pending spec.
  Concatenates :doc strings for nested specs.  Uses ANSI color if
  com.stuartsierra.lazytest.color/colorize? is true."
  [results]
  (newline)
  (doseq [r results]
    (spec-report* r []))
  (newline))

(defn report-and-exit
  "Calls function f (defaults to spec-report) on test result r and
  exits.  Exit status is 0 if all specs passed, -1 if some failed."
  ([r] (report-and-exit spec-report r))
  ([f r]
     (f r)
     (System/exit (if (success? r) 0 -1))))
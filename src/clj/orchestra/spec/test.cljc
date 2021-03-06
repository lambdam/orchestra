(ns orchestra.spec.test
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]))

(defn- no-args-spec
  [v spec]
  (ex-info (str "Args for " v " are not spec'ed.")
           {:var v :spec spec ::s/failure :no-args-spec}))

(defn- spec-checking-fn
  [v f raw-fn-spec]
  (let [fn-spec (#'s/maybe-spec raw-fn-spec)
        conform! (fn [v role spec data data-key]
                   (st/with-instrument-disabled
                     (let [conformed (s/conform spec data)]
                       (if (= ::s/invalid conformed)
                         (let [caller (->> #?(:clj (.getStackTrace (Thread/currentThread))
                                              :cljr (System.Diagnostics.StackTrace. true))
                                           (#'st/stacktrace-relevant-to-instrument)
                                           first)
                               via (if-some [n (#'s/spec-name spec)]
                                     [n]
                                     [])
                               ed (merge (assoc (s/explain-data* spec [] via [] data)
                                                ::s/fn (#'s/->sym v)
                                                data-key data
                                                ::s/failure :instrument)
                                         (when caller
                                           {::caller (dissoc caller :class :method)}))]
                           (throw (ex-info
                                    (str "Call to " v " did not conform to spec.")
                                    ed)))
                         conformed))))]
    (fn
      [& args]
      (if @#'st/*instrument-enabled*
        (let [cargs (when-some [spec (:args fn-spec)]
                      (conform! v :args spec args ::s/args))
              ret (.applyTo ^clojure.lang.IFn f args)
              cret (when-some [spec (:ret fn-spec)]
                     (conform! v :ret spec ret ::s/ret))]
          (when-some [spec (:fn fn-spec)]
            (if (nil? cargs)
              (throw (no-args-spec v fn-spec))
              (conform! v :fn spec {:ret (or cret ret) :args cargs} ::s/fn)))
          ret)
        (.applyTo ^clojure.lang.IFn f args)))))

(defonce patch-clojure (alter-var-root #'st/spec-checking-fn (constantly spec-checking-fn)))

(def instrument st/instrument)
(def unstrument st/unstrument)

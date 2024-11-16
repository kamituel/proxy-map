(ns pl.kamituel.map-proxy.examples.proxy-nested
  (:require
   [pl.kamituel.map-proxy.map-proxy :as proxy]))

;; Auto-proxying of any nested maps will be done if `proxy-nested?` returns a truthy value:

(defn make-handler [proxy-nested?]
  (reify proxy/Handler
    (proxy-nested? [_]
      proxy-nested?)
    (on-get [_ m k]
      (get m k))
    (on-assoc [_ _m k _v]
      (#{:a :b :c} k))
    (on-dissoc [_ _m _k]
      true)))

(defn do-things [m]
  (-> m
      (assoc :a "a"
             :d "d")
      (assoc-in [:b :a] "ba")
      (assoc-in [:b :d] "bd")
      (assoc-in [:b :c :d] "bcd")))

(-> {}
    (proxy/proxy (make-handler false))
    (do-things))
;; {:a "a"
;;  :b {:a "ba"
;;      :d "bd"
;;      :c {:d "bcd"}}}

(-> {}
    (proxy/proxy (make-handler true))
    (do-things))
;; {:a "a"
;;  :b {:a "ba"
;;      :c {}}}

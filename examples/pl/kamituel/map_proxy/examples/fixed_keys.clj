(ns pl.kamituel.map-proxy.examples.fixed-keys
  (:require
   [pl.kamituel.map-proxy.map-proxy :as proxy]))

;; This example shows the usage for `on-assoc `and `on-dissoc `. Proxied map will not allow any
;; values from being removed from the map. It allows `assoc `but only if it wouldn't result in an
;; existing value being overwritten.

(def handler
  (reify proxy/Handler
    (proxy-nested? [_]
      false)
    (on-get [_ m k]
      (get m k))
    (on-assoc [_ m k _v]
      (not (contains? m k)))
    (on-dissoc [_ _m _k]
      false)))

(def m
  (proxy/proxy {:a 1} handler))

;; Cannot overwrite a value that already exists in the map
(assoc m :a 2)
;; => {:a 1}

;; Can assoc a new value (if key was not already present in the map)
(assoc m :b 2)
;; => {:a 1 :b 2}

;; Cannot dissoc a value
(dissoc m :a)
;; => {:a 1}


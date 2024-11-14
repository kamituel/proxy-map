(ns pl.kamituel.map-proxy
  (:require
   [clojure.walk :as walk]))

(declare ->MapProxy)

(defprotocol Handler
  (proxy-nested? [this])
  (on-get [this m k])
  (on-assoc [this m k v])
  (on-dissoc [this m k]))

(declare proxy)

;; See Potemkin's AbstractMap for inspiration.
(deftype MapProxy [m handler]

  clojure.lang.MapEquivalence

  clojure.lang.ILookup
  (valAt [_ k]
    (on-get handler m k))

  clojure.lang.IPersistentCollection
  (equiv [this other-m]
    (.equiv (into {} other-m)
            (into {} this)))
  (cons [this x]
    (cond
      (map? x)
      (reduce #(apply assoc %1 %2) this x)

      (instance? java.util.Map x)
      (reduce #(apply assoc %1 %2) this (into {} x))

      :else
      (if-let [[k v] (seq x)]
        (assoc this k v)
        this)))

  clojure.lang.IPersistentMap
  (assoc [_ k v]
    (MapProxy.
     (if (on-assoc handler m k v)
       (assoc m k (if (and (map? v)
                           (proxy-nested? handler))
                    (proxy v handler)
                    v))
       m)
     handler))
  (without [_ k]
    (MapProxy.
     (if (on-dissoc handler m k)
       (dissoc m k)
       m)
     handler))

  clojure.lang.Associative
  (empty [_]
    (MapProxy. {} handler))

  clojure.lang.Counted
  (count [_]
    (count m))

  clojure.lang.Seqable
  (seq [_]
    (seq
     (map (fn [[k _v]]
            (clojure.lang.MapEntry. k (on-get handler m k)))
          m)))

  java.util.Map
  (get [_ k]
    (on-get handler m k))
  (isEmpty [_]
    (= 0 (count m)))
  (size [_]
    (count m))
  (containsKey [_ k]
    (contains? m k))

  java.lang.Iterable
  (iterator [this]
    (.iterator
     ^java.lang.Iterable
     (.seq this)))

  Object
  (toString [_]
    (str
     (reduce-kv (fn [acc k _v]
                  (assoc acc k (on-get handler m k)))
                {}
                m))))

(defn proxy
  [m handler]
  (if (proxy-nested? handler)
    (walk/postwalk
     (fn [x]
       (if (map? x)
         (->MapProxy x handler)
         x))
     m)
    (->MapProxy m handler)))

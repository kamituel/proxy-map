# map-proxy

A drop-in replacement for the core `{}` map that enables basic proxy functionalities similar to
Javascript's [`Proxy`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy).

## Key Characteristics

- zero dependencies
- provides handlers to alter behaviour of `get`, `assoc`, `dissoc`
- supports nested maps
- API inspired by Javascript `Proxy`

## Usage Examples

Example below implements a map that obscures any secrets within. This could be used to prevent
passwords and secrets from being accidentally printed, logged or stored.

Secrets can be read by providing an explicit namespace to the map key keyword.

```clojure
(require '[pl.kamituel.map-proxy :as proxy])

(def secret-keys
  #{:password
    :token
    :api-key})

(def hide-secrets-handler
  (reify proxy/Handler
    (proxy-nested? [_]
      false)
    (on-get [_ m k]
      (cond
        ;; Hide secrets
        (contains? secret-keys k)
        :hidden-secret

        ;; Allow reading secret when requested explicitely by
        ;; using namespaced keyword
        (= (namespace k) "secret")
        (get m (keyword (name k)))

        ;; All other keys
        :else
        (get m k)))
    (on-assoc [_ _m _k _v]
      true)
    (on-dissoc [_ _m _k]
      true)))

(def config
  (-> {}
      (proxy/proxy hide-secrets-handler)
      (assoc :username     "kate"
             :password     "secret!"
             :api-hostname "localhost"
             :api-key      "another secret!")))

;; Obscures password when printing
(prn config)
;; {:username "kate",
;;  :password :hidden-secret,
;;  :api-hostname "localhost",
;;  :api-key :hidden-secret}

(get config :username)
;; "kate"

;; Obscures passwords when getting using the regular key
(get config :password)
;; :hidden-secret

;; Allows reading password when getting using the namespaced key
(get config :secret/password)
;; "secret!
```

This example shows the usage for `on-assoc` and `on-dissoc`. Proxied map will not allow any values
from being removed from the map. It allows `assoc`, but only if it wouldn't result in an existing
value being overwritten.

```clojure
(require '[pl.kamituel.map-proxy :as proxy])

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
=> {:a 1}

;; Can assoc a new value (if key was not already present in the map)
(assoc m :b 2)
=> {:a 1 :b 2}

;; Cannot dissoc a value
(dissoc m :a)
=> {:a 1}
```

Auto-proxying of any nested maps will be done if `proxy-nested?` returns a truthy value:

```clojure
(require '[pl.kamituel.map-proxy :as proxy])
  
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
```

## Dev

```bash
# Unit tests
./bin/kaocha [--watch]

# REPL with CIDER
clj -M:dev
```
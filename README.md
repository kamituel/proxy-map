# map-proxy

A drop-in replacement for the core `{}` map that enables basic proxy functionalities similar to
Javascript's [`Proxy`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy).

## Usage

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
from removed from the map. It allows `assoc`, but only if it wouldn't result in an existing value
being overwritten.

```clojure
(require '[pl.kamituel.map-proxy :as proxy])

(def handler
  (reify proxy/Handler
    (on-get [_ m k]
      (get m k))
    (on-assoc [_ m k _v]
      (not (contains? m k)))
    (on-dissoc [_ _m _k]
      false)))

(def m
  (proxy/proxy {:a 1} handler))

;; Value was not overwritten
(assoc m :a 2)
=> {:a 1}

(assoc m :b 2)
=> {:a 1 :b 2}

;; Value was not dissoced
(dissoc m :a)
=> {:a 1}
```

## Dev

```bash
# Unit tests
./bin/kaocha [--watch]

# REPL with CIDER
clj -M:dev
```
# map-proxy

A drop-in replacement for the core `{}` map that enables basic proxy functionalities similar to
Javascript's [`Proxy`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy).

- zero dependencies
- provides handlers to alter behaviour of `get`, `assoc`, `dissoc`
- supports nested maps
- API inspired by Javascript [`Proxy`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy)

## Usage

```clojure
(require '[pl.kamituel.map-proxy.map-proxy :as proxy])

(-> ;; Regular map
    {:x 1
     :y 2}
 
    ;; Gets hidden behind a proxy
    (proxy/proxy
     (reify proxy/Handler
       
       ;; Return true for any descendant maps to get
       ;; proxied too.
       (proxy-nested? [this]
         false)

       ;; Called when a value is retrieved. In this
       ;; example it will return a square of that
       ;; value.
       (on-get [this m k]
         (println "Get" k)
         (let [v (get m k)]
           (* v v)))

       ;; Called when a value is set. Return false
       ;; to block assoc.
       (on-assoc [this m k v]
         (println "Assoc" k v)
         true)

       ;; Called when a value is unset. Return false
       ;; to block dissoc.
       (on-dissoc [this m k]
         (println "Dissoc" k)
         true)))

    (assoc :z 3)
    (dissoc :x)
    :y)

 ; Assoc :z 3
 ; Dissoc :x
 ; Get :y
 4
```

Note:

- This form evaluates to `4`, even though the value of `:y` is `2`. This is `on-get` handler
  altering the value on the fly.
- All the `assoc` , `dissoc`, `get` operations got logged.

See more examples in [the `./examples` directory](./examples):

- [`hide-secrets`](./examples/pl/kamituel/map_proxy/examples/hide_secrets.clj) - 
  hide a config map behind a proxy to avoid a risk of accidentally leaking
  or printing secrets.
- [`fixed-keys`](./examples/pl/kamituel/map_proxy/examples/fixed_keys.clj) -
  use a proxy to ensure no keys can be added or removed from a map.
- [`proxy-nested`](./examples/pl/kamituel/map_proxy/examples/proxy-nested.clj) - 
  provide control over a map and all nested maps, recursively.

## Dev

```bash
# Unit tests
./bin/kaocha [--watch]

# REPL with CIDER
clj -M:dev
```
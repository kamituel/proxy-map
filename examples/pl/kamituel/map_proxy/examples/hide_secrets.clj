(ns pl.kamituel.map-proxy.examples.hide-secrets
  (:require
   [pl.kamituel.map-proxy.map-proxy :as proxy]))

;; Example below implements a map that obscures any secrets within. This could be used to prevent
;; passwords and secrets from being accidentally printed, logged or stored.

;; Secrets can be read by providing an explicit namespace to the map key keyword.

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

        ;; Allow reading secret when requested explicitely by using namespaced keyword
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

;; It's possible to get plain values
(get config :username)
;; "kate"

;; But it's not possible to get secret values
(get config :password)
;; :hidden-secret

;; Allows reading password when getting using the namespaced key
(get config :secret/password)
;; "secret!

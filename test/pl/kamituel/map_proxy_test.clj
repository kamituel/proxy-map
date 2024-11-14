(ns pl.kamituel.map-proxy-test
  (:require
   [clojure.test :refer :all]
   [pl.kamituel.map-proxy :as sut]))

(def identity-handler
  (reify sut/Handler
    (proxy-nested? [_]
      false)
    (on-get [_ m k]
      (get m k))
    (on-assoc [_ _m _k _v]
      true)
    (on-dissoc [_ _m _k] 
      true)))

(defn logged-handler [!log proxy-nested? on-assoc-ret on-dissoc-ret]
  (reify sut/Handler
    (proxy-nested? [_]
      proxy-nested?)
    (on-get [_ m k]
      (swap! !log conj [:on-get m k])
      (get m k))
    (on-assoc [_ m k v]
      (swap! !log conj [:on-assoc m k v])
      on-assoc-ret)
    (on-dissoc [_ m k]
      (swap! !log conj [:on-dissoc m k])
      on-dissoc-ret)))

(defn log-for [log f]
  (filter (comp f first) log))

(def test-map
  {:a 1
   :b "b"})

(deftest get-handler 
  (let [!log    (atom [])
        handler (logged-handler !log false true true)
        m-proxy (sut/proxy test-map handler)]
    
    (testing "getting once"
      (:a m-proxy)
      (is (= [[:on-get test-map :a]]
             @!log)))
    
    (testing "getting the second time also triggers on-get"
      (:a m-proxy)
      (is (= [[:on-get test-map :a]
              [:on-get test-map :a]]
             @!log)))
    
    (testing "getting a key that doesn't exist also triggers on-get"
      (:c m-proxy)
      (is (= [[:on-get test-map :a]
              [:on-get test-map :a]
              [:on-get test-map :c]]
             @!log)))))

(deftest assoc-handler
  (testing "assoc with on-assoc returning true"
    (let [!log    (atom [])
          handler (logged-handler !log false true true)
          m-proxy (sut/proxy test-map handler)]
      
      (testing "new value has been assoc'ed"
        (is (= (assoc test-map :x "x")
               (assoc m-proxy :x "x"))))
      
      (testing "handler was invoked with correct arguments"
        (is (= [[:on-assoc test-map :x "x"]]
               (log-for @!log #{:on-assoc}))))))
  
  (testing "assoc with on-assoc returning false"
    (let [!log    (atom [])
          handler (logged-handler !log false false true)
          m-proxy (sut/proxy test-map handler)]
  
      (testing "new value has not been assoc'ed"
        (is (= {:a 1 :b "b"}
               (assoc m-proxy :x "x"))))
  
      (testing "handler was invoked with correct arguments"
        (is (= [[:on-assoc test-map :x "x"]]
               (log-for @!log #{:on-assoc}))))))

  (testing "assoc multiple times"
    (let [!log    (atom [])
          handler (logged-handler !log false true true)
          m-proxy (sut/proxy test-map handler)]

      (is (= (assoc test-map
                    :x "x"
                    :y "y"
                    :z "z"
                    :w "w")
             (-> m-proxy
                 (assoc :x "x")
                 (assoc :y "y")
                 (assoc :z "z"
                        :w "w"))))
      
      (is (= [[:on-assoc {:a 1 :b "b"} :x "x"]
              [:on-assoc {:a 1 :b "b" :x "x"} :y "y"]
              [:on-assoc {:a 1 :b "b" :x "x" :y "y"} :z "z"]
              [:on-assoc {:a 1 :b "b" :x "x" :y "y" :z "z"} :w "w"]]
             (log-for @!log #{:on-assoc}))))))

(deftest dissoc-handler
  (testing "dissoc with on-dissoc returning true"
    (let [!log    (atom [])
          handler (logged-handler !log false true true)
          m-proxy (sut/proxy test-map handler)]

      (testing "value has been dissoc'ed"
        (is (= (dissoc test-map :a)
               (dissoc m-proxy :a))))

      (testing "handler was invoked with correct arguments"
        (is (= [[:on-dissoc test-map :a]]
               (log-for @!log #{:on-dissoc}))))))

  (testing "dissoc with on-dissoc returning false"
    (let [!log    (atom [])
          handler (logged-handler !log false true false)
          m-proxy (sut/proxy test-map handler)]
  
      (testing "value has not been dissoc'ed"
        (is (= {:a 1 :b "b"}
               (dissoc m-proxy :a))))
  
      (testing "handler was invoked with correct arguments"
        (is (= [[:on-dissoc test-map :a]]
               (log-for @!log #{:on-dissoc}))))))

  (testing "dissoc multiple times"
    (let [!log    (atom [])
          handler (logged-handler !log false true true)
          m-proxy (sut/proxy test-map handler)]

      (is (= (dissoc test-map :a :b)
             (-> m-proxy
                 (dissoc :a :b))))

      (is (= [[:on-dissoc {:a 1 :b "b"} :a]
              [:on-dissoc {:b "b"} :b]]
             (log-for @!log #{:on-dissoc}))))))

(deftest serialisation-respects-on-get

  (is (= "{:a 1, :b :replaced}"
         (with-out-str
           (print
            (sut/proxy {:a 1 :b 2}
                       (reify sut/Handler
                         (proxy-nested? [_]
                           false)
                         (on-get [_ m k]
                           (if (= k :b)
                             :replaced
                             (get m k)))))))))

  (is (= "{:a 1, :b :replaced}"
         (.toString
          (sut/proxy {:a 1 :b 2}
                     (reify sut/Handler
                       (proxy-nested? [_]
                         false)
                       (on-get [_ m k]
                         (if (= k :b)
                           :replaced
                           (get m k)))))))))

(deftest seq-respects-on-get
  (is (= [:b :replaced]
         (first
          (sut/proxy {:b 2}
                     (reify sut/Handler
                       (proxy-nested? [_]
                         false)
                       (on-get [_ m k]
                         (if (= k :b)
                           :replaced
                           (get m k))))))))

  (is (= [[:a 1]
          [:b :replaced]]
         (seq
          (sut/proxy {:a 1 :b 2}
                     (reify sut/Handler
                       (proxy-nested? [_] 
                         false)
                       (on-get [_ m k]
                         (if (= k :b)
                           :replaced
                           (get m k))))))))
  
  (is (= [[:a 1]
          [:b :replaced]]
         (iterator-seq
          (.iterator
           (sut/proxy {:a 1 :b 2}
                      (reify sut/Handler
                        (proxy-nested? [_] 
                          false)
                        (on-get [_ m k]
                          (if (= k :b)
                            :replaced
                            (get m k))))))))))

(deftest equiv-respects-on-get
  (let [m-orig  {:a 1 :b 2}
        m-proxy (sut/proxy m-orig
                           (reify sut/Handler
                             (proxy-nested? [_]
                               false)
                             (on-get [_ m k]
                               (if (= k :b)
                                 :replaced
                                 (get m k)))))]
    (is (= {:a 1 :b :replaced}
           m-proxy))
    (is (= m-proxy
           {:a 1 :b :replaced}))
    (is (not= m-orig m-proxy))
    (is (not= m-proxy m-orig))))

(deftest behaves-like-a-map

  (is (= (sut/proxy {:a 1 :b "b"} identity-handler)
         {:a 1 :b "b"})
      "(= proxy map)")

  (is (= {:a 1 :b "b"}
         (sut/proxy {:a 1 :b "b"} identity-handler))
      "(= map proxy)")

  (let [m {:a 1 :b "b"}]
    (are
     [f] (= (f m)
            (f (sut/proxy m identity-handler)))
      count
      seq
      str
      identity
      keys
      vals
      empty
      first
      last
      rest
      ffirst
      #(assoc % :x "x")
      #(dissoc % :a)
      #(filter (comp #{:a} first) %)
      #(contains? % :a)
      #(contains? % :z)
      (comp key first)
      (comp val first)
      #(assoc % :c :cc)
      #(dissoc % :b)
      #(merge % {:x "x"})
      #(merge {:x "x"} %))))

(deftest proxy-nested

  (testing "create a proxy with nested map already present"
      (let [!log     (atom [])
            handler  (logged-handler !log true true true)
            test-map {:a 1
                      :b "b"
                      :x {:y "y"}}
            m-proxy  (sut/proxy test-map handler)]

        (-> m-proxy
            (get-in [:x :y]))

        (is (= [[:on-get {:a 1, :b "b", :x {:y "y"}} :x]
                [:on-get {:y "y"} :y]]
               (log-for @!log #{:on-get})))))

  (testing "assoc a nested map"
    (let [!log    (atom [])
          handler (logged-handler !log true true true)
          m-proxy (sut/proxy test-map handler)]

      (-> m-proxy
          (assoc-in [:x :y] "y")
          (get-in [:x :y]))

      (is (= [[:on-get {:a 1, :b "b"} :x]
              [:on-get {:a 1, :b "b", :x {:y "y"}} :x]
              [:on-get {:y "y"} :y]]
             (log-for @!log #{:on-get})))))

  (testing "assoc a deep nested map"
    (let [!log    (atom [])
          handler (logged-handler !log true true true)
          m-proxy (sut/proxy test-map handler)]

      (-> m-proxy
          (assoc-in [:x :y :z] "z")
          (get-in [:x :y :z]))

      (is (= [[:on-get {:a 1, :b "b"} :x]
              [:on-get {:a 1, :b "b", :x {:y {:z "z"}}} :x]
              [:on-get {:y {:z "z"}} :y]
              [:on-get {:z "z"} :z]]
             (log-for @!log #{:on-get})))))
  
  (testing "get a nested map"
    (let [z (-> {}
                (sut/proxy (logged-handler (atom []) true true true))
                (assoc-in [:x :y :z] "z")
                (get-in [:x :y]))]
      (is (= {:z "z"}
             z))))
  
  (testing "merge"
    (let [handler (reify sut/Handler
                    (proxy-nested? [_]
                      true)
                    (on-get [_ m k]
                      (let [v (get m k)]
                        (if (number? v)
                          (* v v)
                          v)))
                    (on-assoc [_ _m _k _v]
                      true)
                    (on-dissoc [_ _m _k]
                      true))
          m (-> {:a {:b 2}
                 :b {:a 3}}
                (sut/proxy handler)
                (merge {:b {:a 4}
                        :c 5}))]
      (is (= 4 (get-in m [:a :b])))
      (is (= 16 (get-in m [:b :a])))
      (is (= 25 (get-in m [:c]))))))

(comment

  ;; Usage example from README

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

  (prn config)
  (get config :username)
  (get config :password)
  (get config :secret/password))

(comment

  ;; Usage example from README

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

  (assoc m :a 2)
  (assoc m :b 2)
  (dissoc m :a))

(comment

  ;; Usage example from README

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

  (-> {}
      (proxy/proxy (make-handler true))
      (do-things)))

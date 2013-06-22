(ns clj-gamification.test.handler
  (:use clojure.test
        ring.mock.request
        clj-gamification.handler
        [clojure.pprint :only (pprint)]))

;;; TODO Improve the tests
;;; They are convoluted, do too much, rely on accessing internal state at one place,
;;; on reg-exping html at another place
;;; Make the simpler, focused, accessing the app via a uniform and clean interface
;;; It would be lovely to have a JSON API to use instead oh html and internal state

(def HTTP-REDIRECT 302 )
(deftest test-app

  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (= (:status response) 404))))

  (testing "first visitor becomes GameMaster, 2nd+ see group registration page"
    (let [system (system nil)
          app (make-app-routes system)
          resp-1st (app (request :get "/"))
          resp-2nd (app (request :get "/"))]
      ;; Response one: become GM, start brainstorming
      (is (get-in resp-1st [:session :gamemaster?]))
      (is (re-find #"<title>GameMaster Control" (:body resp-1st)))
      (is (= @(:state system) :brainstorming))
      ;; response 2: team tegistration
      (is (not (get-in resp-2nd [:session :gamemaster?])))
      (is (re-find #"<title>Team registration" (:body resp-2nd)))
      ))

  (testing "can register team"
    (let [app (make-app-routes (system nil))
          response (app (request :get "/team"))]
      (is (= (:headers response) {"Location" "/team/2"}))
      (is (= (:status response) HTTP-REDIRECT))))

  (testing "can register team idea"
    (let [system (system nil)
          app (make-app-routes system)
          resp-publish-idea (app (merge
                         (request :post "/team/2/idea")
                         {:params {"idea" "my great idea"}}))]
      (is (= @(:teams system) {2 "my great idea"}))))

  (testing "can vote"
      (let [app (make-app-routes (system {:state (atom :voting)}))
            resp-1st (app (merge (request :post "/vote") {:params {"teamid" "123"}}))]
        (is (= (:status resp-1st) 201))))

  (testing "can vote only once"
      (let [app (make-app-routes (system {:state (atom :voting)}))
            response (app (merge
                           (request :post "/vote")
                           {:params {"teamid" "123"}
                            :session {:voted? true}}))]
        (is (= (:status response) 410))))

  (testing "display /teams"
    (let [app (make-app-routes
               (system {:teams (atom {
                                      1234 "idea of team 1234",
                                      999 "another great idea"})}))
          response (app (request :get "/teams"))
          html (:body response)]
      (is (re-find #"1234" html))
      (is (re-find #"idea of team 1234" html))
      (is (re-find #"999" html))
      (is (re-find #"another great idea" html))))

  (testing "display /vote-results"
    (let [app (make-app-routes
               (system {:state (atom :voting),
                        :votes (atom {
                                      1234 100,
                                      999 77})}))
          response (app (request :get "/vote-results"))
          html (:body response)]
      ;; Note: We do not check order, just the presence of the attributes
      (is (re-find #"1234" html))
      (is (re-find #"100" html))
      (is (re-find #"999" html))
      (is (re-find #"77" html))))

  ;; TODO check display order of teams reflects registration order
  )

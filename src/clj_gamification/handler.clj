(ns clj-gamification.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :refer [html5]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [ring.middleware.file-info :refer :all]
            ;[ring.middleware.file :refer :all]
            [hiccup.bootstrap.middleware :refer [wrap-bootstrap-resources]]
            [hiccup.bootstrap.page :refer :all]
            [clojure.pprint]
            [clojure.tools.logging :refer (info error)]))

(declare reset-state)

(defn toint [numstr] (Integer/parseInt numstr))

(defn page [subtitle & content]
  "Page template"
  (html5
   [:head
    [:title subtitle " :: ScotsGame"]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
    (include-bootstrap)]
   [:body (fixed-layout
           content)]))

(defn page-team-registration []
  "Team self-registration"
  (page
   "Team registration"
   [:h1 "Ready for an awesome discussion about gamification?"]
   [:form {:action "/team"}
    [:button.btn {:type "sumbit"} "Start a new team!"]]))

(defn page-team-idea [team-id idea]
  "Form to submit name of team's gamification idea"
  (page
   "Idea"                               ; TODO better title
   [:form {:action (str "/team/" team-id "/idea"), :method "post"}
    [:label (str "Publish team " team-id "'s gamification idea:")
     [:br]
     [:input {:type "text", :name "idea", :value idea,
              :autofocus "true",
              :title "Describe your idea"}]]
    [:button.btn {:type "submit"} "Publish"]]))

(defn page-teams [teams]
  "Overveiw of all teams and their ideas"
  (page
   "Teams"
   [:p "TODO: 10min countdown"]          ; TODO counter, sort by ID
   (map
    (fn [[id idea]] [:p "#" id ": " idea])  ; TODO show picture, format the list nicely
    teams)))

(defn page-vote [teams]
  "Vote for a gamification idea"
  (page
   "Vote"
   [:h1 "The best gamification idea is:"]
   [:form {:action "/vote", :method "post"}
    (map
     (fn [[id idea]] [:p
                     [:label
                      [:input
                       {:type "radio", :value id, :name "teamid", :required ""} ; TODO required has no effect?
                       "#" id ": " idea]]])  ; TODO show picture, format the list nicely
     teams)
    [:button.btn {:type "submit"} "Vote!"]]))

(defn page-vote-results [teams votes]
  (page
   "Results"
   [:h1 "Voting results:"]
   (let [results (reverse (sort-by second votes))]
     [:ol
      (map
       (fn [[id votes]] [:li votes " votes for team " id " with " (get teams id)]) ; TODO show picture, format the list nicely
       results)])))

(defn- command-js [command] ; TODO include in a script element as a js fun, call it
  "Create JavaScript to perform a GM command in the background"
  (str "var xhr = new XMLHttpRequest();
      xhr.open('POST', '/command/" command "', true);
      xhr.onload = function(e) {alert('done with " command "');}
      xhr.send();
"))

(defn page-gamemaster []
  "Control page for the GameMaster: 1. task info, 2. show task to others, 3. start brainstorming?, 4. start voting, 5. show number voted, 6. show voting results"
  (page
   "GameMaster Control"
   [:h1 "You control the game!"]
   [:p "Your mission, should you decide to accept it: TODO:describe"] ; TODO Hide when read?
   [:h2 "Controls"]
   (letfn [(button [cmd label]
             [:button.btn.btn-large
              {:style "display:block;width:100%;margin-bottom:10px",
               :onclick (command-js cmd)}
              label])]
    [:div
     (button "show-task" "1. Show the task on the projector")
     (button "start-brainstorming" "2. Start brainstorming")
     (button "start-voting" "3. Start voting")
     (button "show-voting-results" "4. Show voting results")])))

(defn has-voted? [{:keys [remote-addr session]}]
  (or
   ;(@voter-ips remote-addr) ; TODO disabled for easier testing from 1 pc
   (:voted? session)))

(defn show-page-for-step [{:keys [teams state]}
                          {:keys [remote-addr session] :as req}]
  "Show the right page for the current stage: gamemaster, team registr., voting"
  (let [first-visitor? (compare-and-set! state :not-started :brainstorming)
        gm? (:gamemaster? session)]
   (cond
    (or gm? first-visitor?) {:session {:gamemaster? true},
                             :body (page-gamemaster)}
    (= @state :brainstorming) (page-team-registration)
    (= @state :voting) (if (has-voted? req)
                           "Thank you for your vote!"
                           (page-vote @teams)))))

;;; Having this fun to create routes to be able to pass system as an argument into them is wierd, seems to g
;;; against the logic of Compojure and is ugly because calling it again will change the var 'app-routes' def.
;;; by the macro. Is there a better way? Should we just put system into an atom (and reset! it before each test?)
;;; (We could also call manually the fn compojure.core/routes in a list of (GET ...) but again against compojure.
;;; (Notice the routes do not change between tests; only the state does but how to pass on other than via the
;;; lexical clojure of make-app-routes or as a global state atom? (Routes same, but depend on the state.)
;;; Possib. 3: create handler that attaches state into request and thus passes is on; ugly as well?
;;; Note: the problem of def could be solved by generating a unique name inst.of 'app-routes'
(defn make-app-routes
  "Create Ring handler, using dependencies from the system as needed."
  [system]
  (let [state (:state system)
        team-counter (:team-counter system)
        teams (:teams system)
        votes (:votes system)
        voter-ips (:voter-ips system)]
      (defroutes app-routes
     "/ - depending on the current stage, show either game master's page, team-registration page, or voting page
   /team - new team registration, idea publication
   /teams - overview of all the existing, registered teams and their ideas
  "
     (GET "/" [:as req]
          (show-page-for-step system req))
     (context "/team" []
              (GET "/" [] (redirect (let [id (swap! (team-counter) inc)] ; TODO assign picture/name of an animal
                                      (swap! teams assoc id "")
                                      (str "/team/" id))))
              (GET "/:id" [id]
                   (let [id (toint id)
                         idea (get @teams id)]
                     (page-team-idea id idea)))
              (POST "/:id/idea" [id idea]
                    (let [id (toint id)]
                      (swap! teams assoc id idea) ; TODO notify visibly that the idea has been published; notify /teams page
                      (redirect (str "/team/" id)))))
     (GET "/teams" []
          (page-teams @teams))
     (POST "/vote" [teamid :as req]
           (if (has-voted? req)
             "Sorry, but according to our shaky records, you have already voted"
             (do
               (swap! votes (partial merge-with + {(toint teamid) 1}))
               ;; two re-voting preventions:
               (swap! voter-ips conj (:remote-addr req))
               {:session {:voted? true},
                :body (str "Thank you for voting for team " teamid "!")})))
     (GET "/vote" [] "Sorry, you can only POST to this page")
     (GET "/vote-results" [] (page-vote-results @teams @votes))
     (GET "/projector" []
          "TODO: Stuff showing on the projector: 1. the task; 2. /teams; 3. /vote-results")
     (context "/command" [] ; GameMaster posts commands here (TODO? check it really is gm)
              (POST "/show-task" [] "TODO")
              #_(POST "/start-brainstorming" [] (str (compare-and-set! state :oldstate :newstate))) ;; CURRENTLY NOT NEEDED
              (POST "/start-voting" [] (str (compare-and-set! state :brainstorming :voting)))
              (POST "/show-voting-results" [] (str (compare-and-set! state :voting :voting-finished))))
     (GET "/reset" [sure]
          (if sure
            (do
              (reset-state system)
              "Done resetting the state!")
            (page "Reset"
                  [:p "Do you really want to reset the state, teams, votes? "
                   [:a {:href "/reset?sure=yes"} "Sure, reset it all!"]])))
     (route/resources "/")
     (route/not-found "Not Found"))))

(defn system
  "Returns a new instance of the whole application."
  [defaults] (merge
           {:session-atom (atom {})
            :state (atom :not-started),
            :team-counter (atom 1),
            :teams (atom {}),
            :votes (atom {}),
            :voter-ips (atom #{})}
           defaults))

(defn init
  "Init the current system, optionally with a default system, returning a Ring handler"
  ([] (init nil))
  ([defaults]
     (let [system (system defaults)]
       (-> (handler/site (make-app-routes system))
           (wrap-session {:store (memory-store (:session-atom system))})
           (wrap-bootstrap-resources)
           (wrap-file-info)))))

;;(def dynamic-app (init))
(def app (init))

(defn myapp [req]
  (let [routes (init)]
    (routes req)))

(defn reset-state
  "Reset GM, teams, votes etc. to be able to start from scratch."
  [system]
  (info "Resetting the state...")
  ;; TODO? we should perhaps sync the restets to do them as 1 atomic operation?
  (reset! (:state system) :not-started)
  (reset! (:team-counter system) 1)
  (reset! (:teams system) {123 "my harcoded after-reset team"})
  (reset! (:votes system) {})
  (reset! (:voter-ips system) #{}))
;; TIME USED: (time-in-hrs + 3.5 1.5 1 + 1) + (13.30 - ?)
;; incl.: learning and setting up heroku (1h), learning/troubleshooting (2+h)

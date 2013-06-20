(ns clj-gamification.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :refer [html5]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [clojure.pprint]))

(def state (atom :not-started))
(def team-counter (atom 1))
(def teams (atom {}))
(def votes (atom {}))
(def voter-ips (atom #{}))
(def session-atom (atom {}))

(defn toint [numstr] (Integer/parseInt numstr))

(defn page [subtitle & content]
  "Page template"
  (html5
   [:head
    [:title subtitle " :: Gamification"]]
   [:body content]))

(defn page-team-registration []
  "Team self-registration"
  (page
   "Team registration"
   [:h1 "Ready for an awesome discussion about gamification?"]
   [:form {:action "/team"}
    [:button {:type "sumbit"} "Start a new team!"]]))

(defn page-team-idea [team-id idea]
  "Form to submit name of team's gamification idea"
  (page
   "Idea"                               ; TODO better title
   [:form {:action (str "/team/" team-id "/idea"), :method "post"}
    [:label (str "Publish team " team-id "'s gamification idea:")
     [:input {:type "text", :name "idea", :value idea, :title "Describe your idea"}]]
    [:button {:type "submit"} "Publish"]]))

(defn page-teams []
  "Overveiw of all teams and their ideas"
  (page
   "Teams"
   [:p "TODO: 10min countdown"]          ; TODO counter, sort by ID
   (map
    (fn [[id idea]] [:p "#" id ": " idea])  ; TODO show picture, format the list nicely
    @teams)))

(defn page-vote []
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
     @teams)
    [:button {:type "submit"} "Vote!"]]))

(defn page-vote-results []
  (page
   "Results"
   [:h1 "Voting results:"]
   (let [results (reverse (sort-by second @votes))]
     [:ol
      (map
       (fn [[id votes]] [:li votes " votes for team " id " with " (get @teams id)]) ; TODO show picture, format the list nicely
       results)])))

(defn- command-js [command] ; TODO include in a script element as a js fun, call it
  "Create JavaScript to perform a GM command in the background"
  (str "var xhr = new XMLHttpRequest();
      xhr.open('POST', '/command/" command "', true);
      xhr.onload = function(e) {console.log('done with " command "');}
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
             [:button
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

(defn show-page-for-step [{:keys [remote-addr session] :as req}]
  "Show the right page for the current stage: gamemaster, team registr., voting"
  (let [first-visitor? (compare-and-set! state :not-started :brainstorming)
        gm? (:gamemaster? session)]
   (cond
    (or gm? first-visitor?) {:session {:gamemaster? true},
                             :body (page-gamemaster)}
    (= @state :brainstorming) (page-team-registration)
    (= @state :voting) (if (has-voted? req)
                           "Thank you for your vote!"
                           (page-vote)))))

(defn reset-state []
  "Reset GM, teams, votes etc. to be able to start from scratch. CAVEATS: Stuff saved in people's session won't be reset."
  ;; => Consider using a random string as part of the session key
  (reset! state :not-started)
  (reset! team-counter 1)
  (reset! teams {})
  (reset! votes {})
  (reset! voter-ips #{})
  (clojure.pprint/pprint @session-atom)
  (reset! session-atom {}))

(defroutes app-routes
  "/ - depending on the current stage, show either game master's page, team-registration page, or voting page
   /team - new team registration, idea publication
   /teams - overview of all the existing, registered teams and their ideas
  "
  (GET "/" [:as req]
       (show-page-for-step req))
  (context "/team" []
           (GET "/" [] (redirect (let [id (swap! team-counter inc)]  ; TODO assign picture/name of an animal
                                       (swap! teams assoc id "")
                                       (str "/team/" id))))
           (GET "/:id" [id]
                (let [id (toint id)
                      idea (get @teams id)]
                  (page-team-idea id idea)))
           (POST "/:id/idea" [id idea]
                 (let [id (toint id)]
                   (swap! teams assoc id idea)  ; TODO notify visibly that the idea has been published; notify /teams page
                   (redirect (str "/team/" id)))))
  (GET "/teams" []
       (page-teams))
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
  (GET "/vote-results" [] (page-vote-results))
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
           (reset-state)
           "Done resetting the state!")
         (page "Reset"
               [:p "Do you really want to reset the state, teams, votes? "
                [:a {:href "/reset?sure=yes"} "Sure, reset it all!"]])))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (wrap-session {:store (memory-store session-atom)})))

;; TIME USED: (time-in-hrs + 3.5 1.5 1)

(ns clj-gamification.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :refer [html5]]
            [ring.util.response :refer [redirect]]))

(def state (atom :not-started))
(def team-counter (atom 1))
(def teams (atom {999 "Team 9's idea"}))
(def votes (atom {998 1, 999 3}))

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
   [:p "TODO: 10min countdown"]          ; TODO
   (map
    (fn [[id idea]] [:p "#" id ": " idea])  ; TODO show picture, format the list nicely
    @teams)))

(defn page-vote []
  "Vote for a gamification idea"
  (page
   "Vote"
   [:h1 "The best gamification idea is:"]          ; TODO
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

(defn page-gamemaster []
  "Control page for the GameMaster: 1. task info, 2. show task to others, 3. start brainstorming?, 4. start voting, 5. show number voted, 6. show voting results"
  (page
   "GameMaster Control"
   [:h1 "You control the game!"]
   [:p "Your mission, should you decide to accept it: TODO:describe"] ; TODO Hide when read?
   [:h2 "Controls"]
   (let [bs {:style "display:block;width:100%;margin-bottom:10px"}]
    [:div
     [:button bs "1. Show the task on the projector"]
     [:button bs "2. Start brainstorming"]
     [:button bs "3. Start voting"]
     [:button bs "4. Show voting results"]])))

(defn show-page-for-step []
  "Show the right page for the current stage: gamemaster, team registr., voting"
  (let [first-visitor? (compare-and-set! state :not-started :brainstorming)]
   (cond
     first-visitor? (page-gamemaster)  ; TODO set cookie to remember the gamemaster
     (= @state :brainstorming) (page-team-registration)
     (= @state :voting) (page-vote))))

(defroutes app-routes
  "/ - depending on the current stage, show either game master's page, team-registration page, or voting page
   /team - new team registration, idea publication
   /teams - overview of all the existing, registered teams and their ideas
  "
  (GET "/" [] (show-page-for-step))
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
  (POST "/vote" [teamid] ; FIXME parse into int
        (swap! votes (partial merge-with + {(toint teamid) 1}))
        ;; TODO set cookie to prevent multiple votes
        (str "Thank you for woting for team " teamid "!"))
  (GET "/vote" [] "Sorry, you can only POST to this page")
  (GET "/vote-results" [] (page-vote-results))
  (GET "/projector" []
       "TODO: Stuff showing on the projector: 1. the task; 2. /teams; 3. /vote-results")
  (context "/command" [] ; GameMaster posts commands here
           (POST "show-task" [] "TODO")
           (POST "start-brainstorming" [] "TODO")
           (POST "start-voting" [] "TODO")
           (POST "show-voting-results" [] "TODO"))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

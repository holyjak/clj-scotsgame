(ns clj-gamification.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :refer [html5 include-js]]
            [org.httpkit.server :refer :all #_[run-server with-channel Channel]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [ring.middleware.file-info :refer :all]
            ;[ring.middleware.file :refer :all]
            [hiccup.bootstrap.middleware :refer [wrap-bootstrap-resources]]
            [hiccup.bootstrap.page :refer :all]
            [clojure.pprint]
            [clojure.tools.logging :refer (info error)]
            [clojure.data.json :as json])
  (:gen-class))

(declare reset-state)

(defn toint [numstr] (Integer/parseInt numstr))

(defn include-projectorpoll-js [current-state]
  [:script {:type "text/javascript"} "window.onload=function(){projector_poll('" current-state "');};"])

(defn page [subtitle & content]
  "Page template"
  (html5
   [:head
    [:title subtitle " :: ScotsGame"]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
    (include-bootstrap)
    (include-js "/js/scotsgame.js")
    [:style {:type "text/css"} "h1 {font-size:180%} h2 {font-size:160%}"]]
   [:body (fixed-layout
           content
           [:p {:style "font-size:xx-small;border-top:1px solid grey;margin-top:3em;text-align:right;"} "Powered by Clojure"])]))

(def await-voting-html
  [:p "Or "
    [:a {:href "/await-vote"} "await voting for the best idea"]
    " to be started"])

(defn page-team-registration []
  "Team self-registration"
  (page
   "Team registration"
   [:h1 "Ready for an awesome discussion about gamification?"]
   [:form#newGroupForm {:action "/team"}
    [:button.btn {:type "sumbit"} "Start a new team!"]]
   await-voting-html))

(defn page-team-idea [team-id idea published]
  "Form to submit name of team's gamification idea"
  (page
   "Idea"                               ; TODO better title
   (when published
     [:p.text-success "Your idea has been published"])
   [:form {:action (str "/team/" team-id "/idea"), :method "post"}
    [:label (str "Publish team " team-id "'s gamification idea:")
     [:br]
     [:input {:type "text", :name "idea", :value idea,
              :autofocus "true",
              :title "Describe your idea"}]]
    [:button.btn {:type "submit"} "Publish"]]
   await-voting-html
   ))

(defn page-teams [teams]
  "Overveiw of all teams and their ideas"
  (page
   "Teams"
   (include-projectorpoll-js "teams")
   [:h1 "Teams & Topics"]
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

(defn page-gamemaster
  "Control page for the GameMaster: 1. task info, 2. show task to others, 3. start brainstorming?, 4. start voting, 5. show number voted, 6. show voting results"
  []
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
    [:div#gmControls
     (button "show-task" "1. Show the task on the projector")
     (button "start-brainstorming" "2. Start brainstorming")
     (button "start-voting" "3. Start voting")
     (button "show-voting-results" "4. Show voting results")])))

(defn page-await-vote [referer]
  (page "Awaiting voting..."
        [:p "Do "
         [:a {:href "/"} "vote for the best idea"]
         " once voting is opened"]
        (when referer
          [:p "(Or "
           [:a {:href referer} "go back"]
           " where you came from.)"])))

(defn page-projector-prestart []
  (page "Projector"
        (include-projectorpoll-js "prestart")
        [:p {:style "text-align:center;font-size:200px;line-height:200px;margin:auto"} "?"]))

(defn page-projector-task []
  (page "Projector"
        (include-projectorpoll-js "task")
        [:h1 "The Gamification Challenge"]
        [:p "Your task is ..."]))

(defn page-projector-voting-ongoing []
  (page "Projector"
        (include-projectorpoll-js "voting")
        [:h1 "Voting in progress..."]
        ))

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


;;; For a simple example of WS usage in the browser see http://www.html5rocks.com/en/tutorials/websockets/basics/
;;; var connection = new WebSocket('ws://me:8080/ws')
;;; connection.onmessage = function (e) { console.log('Server: ' + e.data); }; // also onerror, onopen
;;; connection.send('your message');
(defn ws-handler "Websocket handler for http-kit" [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [event-json] ;; echo it back; data = e.g. String - see clojure.data.json
                          (let [{:strs [event data] :as m} (json/read-str event-json)]
                            (send! channel (json/write-str {:event "pong", :data data})))))) ;; TODO
  )

(defn watch-projector-state
  "Notify all waiters that the value of projector has changed; triggerd by add-watch on it."
  [_ projector oldval newval]
  (locking projector
    (.notifyAll projector)))

(defn poll-handler-projector [request projector current-display]
  (with-channel request channel
    (when (= current-display (name @projector))
      (locking projector
        (try
          (.wait projector))
        (catch InterruptedException e (info (str "Interrupted while waiting for projector change: " e)))))
    (send! channel {:status 200
                    :headers {"Content-Type" "application/json; charset=utf-8"}
                    :body (name @projector)}
                                        ; send! and close. just be explicit, true is the default for streaming/polling,
           true)))

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
        voter-ips (:voter-ips system)
        projector (:projector system)]
      (defroutes app-routes
     "/ - depending on the current stage, show either game master's page, team-registration page, or voting page
   /team - new team registration, idea publication
   /teams - overview of all the existing, registered teams and their ideas
  "
     (GET "/" [:as req]
          (show-page-for-step system req))
     (context "/team" []
              (GET "/" [] (redirect (let [id (swap! team-counter inc)] ; TODO assign picture/name of an animal
                                      (swap! teams assoc id "")
                                      (str "/team/" id))))
              (GET "/:id" [id published]
                   (let [id (toint id)
                         idea (get @teams id)]
                     (page-team-idea id idea published)))
              (POST "/:id/idea" [id idea]
                    (let [id (toint id)]
                      (swap! teams assoc id idea) ; TODO notify visibly that the idea has been published; notify /teams page
                      (redirect (str "/team/" id "?published=true")))))
     (GET "/teams" []
          (page-teams @teams))
     (POST "/vote" [teamid :as req]
           (if (has-voted? req)
             {:status 410,
              :body "Sorry, but according to our shaky records, you have already voted"}
             (do
               (swap! votes (partial merge-with + {(toint teamid) 1}))
               ;; two re-voting preventions:
               (swap! voter-ips conj (:remote-addr req))
               {:session {:voted? true},
                :status 201
                :body (page "Voted" [:p "Thank you for voting for team " teamid "!"])})))
     (GET "/await-vote" [:as req]
          (if (= :voting state)
            (redirect "/vote")
            (page-await-vote (get-in req [:headers "referer"]))))
     (GET "/vote" [] "Sorry, you can only POST to this page")
     (GET "/vote-results" [] (page-vote-results @teams @votes))
     (GET "/projector" []
          (condp = @projector
            :prestart (page-projector-prestart)
            :task (page-projector-task)
            :teams (page-teams @teams)
            :voting (page-projector-voting-ongoing)
            :results (page-vote-results @teams @votes)))
     (context "/command" [] ; GameMaster posts commands here (TODO? check it really is gm)
              (POST "/show-task" [] (str (compare-and-set! projector :prestart :task)))
              (POST "/start-brainstorming" [] (str (compare-and-set! projector :task :teams)))
              (POST "/start-voting" [] (str (and
                                             (compare-and-set! state :brainstorming :voting)
                                             (swap! projector (constantly :voting)))))
              (POST "/show-voting-results" [] (str (and
                                                    (compare-and-set! state :voting :voting-finished)
                                                    (swap! projector (constantly :results))))))
     (GET "/reset" [sure]
          (if sure
            (do
              (reset-state system)
              "Done resetting the state!")
            (page "Reset"
                  [:p "Do you really want to reset the state, teams, votes? "
                   [:a {:href "/reset?sure=yes"} "Sure, reset it all!"]])))
     (GET "/ws" [] ws-handler)
     (context "/poll" []
              (GET "/projector-state" [current :as req] (poll-handler-projector req projector current)))
     (route/resources "/") ; TODO Cache-Control: max-age; see also  ring-etag-middleware
     (route/not-found "Not Found"))))

(defn system
  "Returns a new instance of the whole application."
  [defaults] (let [result (merge
                            {:session-atom (atom {})
                             :state (atom :not-started),
                             :team-counter (atom 1),
                             :teams (atom {}),
                             :votes (atom {}),
                             :voter-ips (atom #{})
                             :projector (atom :prestart)}
                            defaults)]
               (add-watch (:projector result) :projector-display watch-projector-state)
               result))

(defn init
  "Init the current system, optionally with a default system, returning a Ring handler"
  ([] (init nil))
  ([defaults]
     (let [system (system defaults)]
       (-> (handler/site (make-app-routes system))
           (wrap-session {:store (memory-store (:session-atom system))})
           (wrap-bootstrap-resources)
           (wrap-file-info)))))

(def app (init))

(defn reset-state
  "Reset GM, teams, votes etc. to be able to start from scratch."
  [system]
  (info "Resetting the state...")
  ;; TODO? we should perhaps sync the restets to do them as 1 atomic operation?
  (reset! (:state system) :not-started)
  (reset! (:team-counter system) 1)
  (reset! (:teams system) {})
  (reset! (:votes system) {})
  (reset! (:voter-ips system) #{}))
;; TIME USED: (time-in-hrs + 3.5 1.5 1 + 1) + (5h lørdag, 1t søndag)
;; incl.: learning and setting up heroku (1h), learning/troubleshooting (2+h)

(def ^{:static true} server-stop-fn (atom nil))

(defn -main [port] ;; entry point
  (let [port (Integer/parseInt port)
        server-stop (run-server app {:port port})]
    (swap! server-stop-fn (constantly server-stop))
    (str "Started at port " port)))

(comment ;; for use in REPL
  (-main "5000")
  )

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
            [hiccup.bootstrap.middleware :refer [wrap-bootstrap-resources]]
            [hiccup.bootstrap.page :refer :all]
            [clojure.pprint]
            [clojure.string]
            [clojure.set :refer [intersection]]
            [clojure.tools.logging :refer (info error)]
            [clojure.data.json :as json])
  (:gen-class))

(declare reset-state)

(def change-events (atom [])) ;; TODO move to the state object
(def change-callbacks (atom #{}))
(defn toint [numstr] (Integer/parseInt numstr))

(defn include-changepoll-js [& event-keys]
  (let [last-event-id (max 0 (dec (count @change-events)))
        events (clojure.string/join "," (map name event-keys))]
    [:script {:type "text/javascript"} "window.onload=function(){pollForChange(" last-event-id ",'" events "');};"]))

(defn page-configurable "Page template" [subtitle footer? content]
  (html5
   [:head
    [:title subtitle " :: ScotsGame"]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
    (include-bootstrap)
    (include-js "/js/scotsgame.js")
    [:style {:type "text/css"} "h1 {font-size:180%} h2 {font-size:160%}"]]
   [:body (fixed-layout
           content
              (when footer?
                  [:p {:style "font-size:xx-small;border-top:1px solid grey;margin-top:3em;text-align:right;"} "Powered by Clojure"]))]))

(defn page-plain [subtitle & content]
    (page-configurable subtitle false content))

(defn page [subtitle & content]
    (page-configurable subtitle true content))

(def await-voting-html
  [:p "Or "
    [:a {:href "/await-vote"} "await voting for the best idea"]
    " to be started"])

(def gamification-ideas ["Increase trashbin usage in parks." "Increase e-book reading platform usage." "Increase consumption of vegetables." "Increase motivation to try/learn new stuff."])

(defn page-team-await-brainstorming "Participants wait until brainstorming started" []
  (page
   "Awaiting brainstorming"
      (include-changepoll-js :state)
      [:h1 "Waiting for brainstorming to start..."]))

(defn page-team-idea "Form to submit name of team's gamification idea"
    ([] (page-team-idea nil nil false))
    ([team-id idea published]
        (page
            "Idea"
            (when published
                [:p.text-success "Idea of team #" team-id " has been published"])
            [:form {:action (str "/team" (if team-id (str "/" team-id) "") "/idea"), :method "post"}
                [:h1 (str "Team/idea publication:")]
                [:p "Get togehther with people around you and come up with the best gamification idea!"]
                [:br]
                [:ul.unstyled
                    (map (fn [id] [:li [:label.radio
                                          [:input {:type "radio", :name "idea", :value id}]
                                          " "
                                          id]
                                     [:br]])
                        gamification-ideas)
                    [:li
                        [:label.radio
                            [:input#custIdeaIn {:type "radio", :name "idea"}
                                [:input {:type "text", :value idea,
                                            :autofocus "true",
                                            :placeholder "Your own idea",
                                            :title "Describe your idea"
                                            :onchange "document.getElementById('custIdeaIn').value=this.value;"}]]]]]
                [:button.btn {:type "submit"} "(Re-)Publish"]]
            await-voting-html
            )))

(defn page-teams [teams]
  "Overveiw of all teams and their ideas"
  (page
   "Teams"
   (include-changepoll-js :projector :teams)
   [:h1 "Teams & Topics"]
   [:p [:strong "Brainstorming time remaining: "
        [:span#countdown {:style "font-size:150%"} "10"]
        " min"]]
   [:p "Teams:"]
   [:ol (map
      (fn [[id idea]] [:li "#" id ": " idea])  ; TODO show picture
      (sort-by first teams))]
   [:h4 "Ideas structuring tip: D6"]
   [:ol
    [:li "DEFINE business objectives: ..."]
    [:li "DELINEATE target behaviors: ..."]
    [:li "DESCRIBE your players: ..."]
    [:li "DEVISE activity loops: ..."]
    [:li "[5+6] DON’T forget the FUN & DEPLOY the appropriate tools: ..."]]
   [:script {:type "text/javascript"} "countdown('countdown');"]))

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
      (let [results (reverse (sort-by second votes))
               first-team (first (sort (keys teams)))]
          [:div
              [:ol
                  (map
                      (fn [[id votes]] [:li votes " votes for team " id " with " (get teams id)]) ; TODO show picture, format the list nicely
                      results)]
              [:p "The winning team and the first team registered (#"
                  first-team
                  "): please come to the stage!"]])))

(defn- command-js [command] ; TODO include in a script element as a js fun, call it
  "Create JavaScript to perform a GM command in the background"
  (str "var xhr = new XMLHttpRequest();
      xhr.open('POST', '/command/" command "', true);
      xhr.onload = function(e) {alert('done with " command ": ' + this.responseText);}
      xhr.send();
"))

(defn page-gamemaster
  "Control page for the GameMaster: 1. task info, 2. show task to others, 3. start brainstorming?, 4. start voting, 5. show number voted, 6. show voting results"
  []
  (page
   "GameMaster Control"
      [:h1 "You control the game!"]
      [:p "As you were the first to come here, you have the unique role of controlling the game! Please go to the stage and present yourself. Thank you!"]
      [:p "Your mission, should you decide to accept it: Challenge the participants to group and brainstorm in 10 min interesting gamification ideas, present them, and then vote for the best one."]
      [:p "Use the 4 buttons below in order to move between the phases, changing what's shown on the projector and on other participants' screens."] ; TODO Hide when read?
      [:h2 "Controls"]
   (letfn [(button [cmd label]
             [:button.btn.btn-large
              {:style "display:block;width:100%;margin-bottom:10px",
               :onclick (command-js cmd)}
              label])]
       [:div#gmControls                 ; TODO add css to make :p italic
           [:p "Ready? Let other's know what to do by pressing button 1!"]
           (button "show-task" "1. Show the task on the projector")
           [:p "Everybody has read the task? Then allow them to register teams by pressing #2!"]
           (button "start-brainstorming" "2. Start brainstorming")
           [:p "Give them 10 min to form teams and brainstorm (see the projector), then let them vote for the best idea by pressing #3!"]
           (button "start-voting" "3. Start voting")
           [:p "After few minuts (watch the projector), show results of the voting!"]
           (button "show-voting-results" "4. Show voting results")])))

(defn page-projector-prestart []
  (page-plain "Projector"
        (include-changepoll-js :projector)
        [:p {:style "text-align:center;font-size:200px;line-height:200px;margin:auto"} "?"]))

(defn page-projector-task []
  (page "Projector"
        (include-changepoll-js :projector)
        [:h1 "The Gamification Challenge"]
        [:p "Gamification in praxis! Group with the people around and brainstorm a cool way to gamify a common task/problem. Describe briefly your idea to the other teams and vote for the best one."]
        [:p "Some ideas for what to increase via gamification: Trashbin usage in parks. E-book reading platform usage. Consumption of vegetables. Motivation to try/learn new stuff. (Listed also on the team registration page.)"]
      [:p "Be quick: The 1st registered team gets a nice surprise!"]
      [:p "Be inventive: The best idea gets a nice surprise too!"]))

;;; Registered teams overview - see page-teams

(defn page-await-vote [referer]
    (page "Awaiting voting..."
        (include-changepoll-js :state)
        [:p "Do "
         [:a {:href "/"} "vote for the best idea"]
            " once voting is opened"]
        "(You will be redirected there automatically.)"
        (when referer
          [:p "(Or "
           [:a {:href referer} "go back"]
           " where you came from.)"])))

(defn page-projector-voting-ongoing [votes]
  (page "Projector"
        (include-changepoll-js :projector :state :votes)
        [:h1 "Voting in progress..."]
        [:p "Voters so far: " (apply + (vals votes))]
        ))

(defn has-voted? [{:keys [remote-addr session]}]
  (or
   (:voted? session)))

(defn show-page-for-step
    "Show the right page for the current stage: gamemaster, team registr., voting"
    [{:keys [teams state] :as system}
        {:keys [remote-addr session] :as req}]
    (let [first-visitor? (compare-and-set! state :not-started :task-intro)
             gm? (:gamemaster? session)]
        (cond
            (or gm? first-visitor?) (do
                                        (info (str "GM access; first? " first-visitor? ", session id" (-> req :cookies (get "ring-session") :value) ", ip " (:remote-addr req) ", sess " session ", sess.store: " @(:session-atom system)))
                                        {:session {:gamemaster? true},
                                            :body (page-gamemaster)})
            (= @state :task-intro) (page-team-await-brainstorming)
            (= @state :brainstorming) (page-team-idea)
            (= @state :voting) (if (has-voted? req)
                                   "Thank you for your vote!"
                                   (page-vote @teams)))))

(defn notify-change-listeners
  "Notify waiters that the value of the atom has changed"
  [event _ _ _]
  (swap! change-events conj event)
  (doseq [notify @change-callbacks]
    (if (notify @change-events)
      (swap! change-callbacks disj notify))))

(defn new-events-filtered
  "Return events newer than the given last one and present in the filter list or an empty collection"
  [events last-event-id filter]
  (let [filter (set filter)]
    (-> (drop last-event-id events)
        set
        (intersection filter))))

(defn poll-handler [last-event-id event-names request]
  ;; Callback return true if it has done its job and should be removed from the callback queue
  (let [last-event-id (toint last-event-id)
        desired-events (map keyword (clojure.string/split event-names (re-pattern ",")))]
    (with-channel request channel
     (swap!
      change-callbacks
      conj
      (fn [change-events]
        (let [new-events (new-events-filtered change-events last-event-id desired-events)]
          (info (str "Notifying about new events;evts" new-events ",for lastId " last-event-id " and filter " (vec desired-events) "; las 10 evts " (take-last 10 change-events))) ;; TODO remove this log
         ;; check if match any of the desired ones, send them if true
          (if (seq new-events)
            (do
              (send! channel {:status 200
                              :headers {"Content-Type" "application/json; charset=utf-8"}
                              :body (clojure.data.json/write-str new-events)})
                true)
            false)))))))

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
              (GET "/" [] (page-team-idea))
              (GET "/:id" [id published]
                  (let [id (toint id)
                           idea (get @teams id)]
                      (page-team-idea id idea published)))
              (POST "/idea" [idea]      ; post new idea
                  (let [id (swap! team-counter inc)] ; TODO assign picture/name of an animal
                      (swap! teams assoc id idea)
                      (redirect (str "/team/" id "?published=true"))))
              (POST "/:id/idea" [id idea] ; update an idea
                    (let [id (toint id)]
                      (info (str "idea posted: " idea))
                      (swap! teams assoc id idea)
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
               (swap! voter-ips conj (:remote-addr req))  ; remove, unused?
               {:session {:voted? true},
                :status 201
                :body (page "Voted" [:p "Thank you for voting for team " teamid "!"])})))
     (GET "/await-vote" [:as req]
          (if (= :voting @state)
            (redirect "/")
            (page-await-vote (get-in req [:headers "referer"]))))
     (GET "/vote" [] "Sorry, you can only POST to this page")
     (GET "/vote-results" [] (page-vote-results @teams @votes))
     (GET "/projector" []
          (condp = @projector
            :prestart (page-projector-prestart)
            :task (page-projector-task)
            :teams (page-teams @teams)
            :voting (page-projector-voting-ongoing @votes)
            :results (page-vote-results @teams @votes)))
     (context "/command" [] ; GameMaster posts commands here
              (POST "/show-task" [] (str (compare-and-set! projector :prestart :task)))
         (POST "/start-brainstorming" [] (str (boolean (and
                                                           (compare-and-set! state :task-intro :brainstorming)
                                                           (compare-and-set! projector :task :teams)))))
              (POST "/start-voting" [] (str (boolean (and
                                                      (compare-and-set! state :brainstorming :voting)
                                                      (swap! projector (constantly :voting))))))
              (POST "/show-voting-results" [] (str (boolean (and
                                                             (compare-and-set! state :voting :voting-finished)
                                                             (swap! projector (constantly :results)))))))
     (GET "/reset" [sure]
          (if sure
            (do
              (reset-state system)
              "Done resetting the state!")
            (page "Reset"
                  [:p "Do you really want to reset the state, teams, votes? "
                   [:a {:href "/reset?sure=yes"} "Sure, reset it all!"]])))
     (context "/poll" []
              (GET "/state-change" [last-event-id event-names :as req] (poll-handler last-event-id event-names req)))
     (route/resources "/") ; TODO Cache-Control: max-age; see also  ring-etag-middleware
     (route/not-found "Not Found"))))

(defn system
  "Returns a new instance of the whole application."
  [defaults] (let [result (merge
                            {:session-atom (atom {}),
                             :state (atom :not-started),
                             :team-counter (atom 1),
                             :teams (atom {}),
                             :votes (atom {}),
                             :voter-ips (atom #{})
                             :projector (atom :prestart)}
                            defaults)]
               (doseq [[key atom] result :when (not (#{:session-atom :team-counter} key))]
                 (add-watch atom key notify-change-listeners))
               result))

(defn reset-state
  "Reset GM, teams, votes etc. to be able to start from scratch."
  [system]
  (info "Resetting the state...")
  ;; TODO? we should perhaps sync the restets to do them as 1 atomic operation?
  (reset! (:state system) :not-started)
  (reset! (:team-counter system) 1)
  (reset! (:teams system) {})
  (reset! (:votes system) {})
  (reset! (:voter-ips system) #{})
  (reset! (:projector system) :prestart)
  (reset! (:session-atom system) {}))

(defonce ^{:doc "Reference to the latest system created to have access to it from the REPL; don't use anywhere else"} current-system-for-repl (atom nil))

(defn init
  "Init the current system, optionally with a default system, returning a Ring handler"
  ([] (init nil))
  ([defaults]
     (let [system (system defaults)]
       (swap! current-system-for-repl (constantly system))
       (-> (handler/site (make-app-routes system)
                         {:session {:store (memory-store (:session-atom system))}})
           (wrap-bootstrap-resources)
           (wrap-file-info)))))

(def app (init))

;; TIME USED: (time-in-hrs + 3.5 1.5 1 + 1) + (5h lørdag, 1t søndag)
;; incl.: learning and setting up heroku (1h), learning/troubleshooting (2+h)

(defonce ^{:static true} server-stop-fn (atom nil))

(defn -main [port] ;; entry point
  (let [port (Integer/parseInt port)
        server-stop (run-server app {:port port})]
    (swap! server-stop-fn (constantly server-stop))
    (str "Started at port " port)))

(comment ;; for use in REPL
  (-main "5000")
  @current-system-for-repl
  )

(ns clj-gamification.handler
  (:use compojure.core
;;        ring.middleware.session.store ;; TODO delete
        )
;;  (:import java.util.UUID) ;; TODO rm
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
   "Idea"
   (when published
     [:p.text-success "Your idea has been published"])
   [:form {:action (str "/team/" team-id "/idea"), :method "post"}
    [:h1 (str "Publish team " team-id "'s gamification idea:")]
    [:br]
    (let [ideas ["Increase trashbin usage in parks." "Increase e-book reading platform usage." "Increase consumption of vegetables." "Increase motivation to try/learn new stuff."]]
      [:ul.unstyled
       (map (fn [id] [:li [:label.radio
                          [:input {:type "radio", :name "idea", :value id}]
                          " "
                          id]
                     [:br]])
            ideas)
       [:li
        [:label.radio
         [:input#custIdeaIn {:type "radio", :name "idea"}
          [:input {:type "text", :value idea,
                   :autofocus "true",
                   :placeholder "Your own idea",
                   :title "Describe your idea"
                   :onchange "document.getElementById('custIdeaIn').value=this.value;"}]]]]])
    [:button.btn {:type "submit"} "Publish"]]
   await-voting-html
   ))

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
   (let [results (reverse (sort-by second votes))]
     [:ol
      (map
       (fn [[id votes]] [:li votes " votes for team " id " with " (get teams id)]) ; TODO show picture, format the list nicely
       results)])))

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
   [:p "Your mission, should you decide to accept it: Challenge the participants to group and brainstorm in 10 min interesting gamification ideas, present them, and then vote for the best one."]
   [:p "Use the buttons below in order to move between the phases, changing what's shown on the projector."] ; TODO Hide when read?
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
        (include-changepoll-js :projector)
        [:p {:style "text-align:center;font-size:200px;line-height:200px;margin:auto"} "?"]))

(defn page-projector-task []
  (page "Projector"
        (include-changepoll-js :projector)
        [:h1 "The Gamification Challenge"]
        [:p "Gamification in praxis! Group with the people around and brainstorm a cool way to gamify a common task/problem. Describe briefly your idea to the other teams and vote for the best one."]
        [:p "Some ideas for what to increase via gamification: Trashbin usage in parks. E-book reading platform usage. Consumption of vegetables. Motivation to try/learn new stuff. (Listed also on the team registration page.)"]
        [:p "Be quick: The 1st and 3rd registered teams get a nice surprise!"]))

(defn page-projector-voting-ongoing []
  (page "Projector"
        (include-changepoll-js :projector :state)
        [:h1 "Voting in progress..."]
        ))

(defn has-voted? [{:keys [remote-addr session]}]
  (or
   (:voted? session)))

(defn show-page-for-step [{:keys [teams state]}
                          {:keys [remote-addr session] :as req}]
  "Show the right page for the current stage: gamemaster, team registr., voting"
  (let [first-visitor? (compare-and-set! state :not-started :brainstorming)
        gm? (:gamemaster? session)]
   (cond
    (or gm? first-visitor?) (do
                              (info (str "GM access; first? " first-visitor? ", session id" (-> req :cookies (get "ring-session") :value) ", ip " (:remote-addr req) ", sess " session))
                              {:session {:gamemaster? true},
                              :body (page-gamemaster)})
    (= @state :brainstorming) (page-team-registration)
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
          (info (str "Notifying about new events;evts" new-events ",for lastId " last-event-id " and filter " (vec desired-events) "; all evts " change-events)) ;; TODO remove this log
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
              (GET "/" [] (redirect (let [id (swap! team-counter inc)] ; TODO assign picture/name of an animal
                                      (swap! teams assoc id "")
                                      (str "/team/" id))))
              (GET "/:id" [id published]
                   (let [id (toint id)
                         idea (get @teams id)]
                     (page-team-idea id idea published)))
              (POST "/:id/idea" [id idea]
                    (let [id (toint id)]
                      (info (str "idea posted: " idea))
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
     (context "/command" [] ; GameMaster posts commands here
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
               (add-watch (:projector result) :projector notify-change-listeners)
               (add-watch (:teams result) :teams notify-change-listeners)
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
  (reset! (:projector system) :prestart))

(def current-system-for-repl "Reference to the latest system created to have access to it from the REPL; don't use anywhere else" (atom nil))

;; TODO It seems that reset of session doesn't work properly but the problem is likely somehwere else
;; Sometimes nothing is written into the session atom, which is weird. This works, results in discovering
;; non-exist. session and creating/writing a new one:
;; (def sys (system {}))
;; (def h (-> #(show-page-for-step sys %) (wrap-session {:store (MyMemoryStore. tmp-sess-atm)})))
;; (h {:Iam "req", :headers {"cookie" "ring-session=my-dummy-sess-key;Path=/"}})
;; =>
;; Reading session my-dummy-sess-key from #<Atom@2c826d6: {}> => nil
;; Written session  9dd1968f-e33a-4f4a-a058-8a5297ae7b0a -> {:gamemaster? true}  to  #<Atom@2c826d6: {9dd1968f-e33a-4f4a-a058-8a5297ae7b0a {:gamemaster? true}}>
;; TBD Verify with a browser with existing cookie that the value gets updated;
;; Then, why is the tmp-sess-atom always empty??
;; !!!! ANONYM. WINS IN CHROME SHARE COOKIES -> set in one, E in all - so it seems
;;(def tmp-sess-atm (atom {}))
;; FIXME: Open anon. FF -> become GM; reload in Chrome that was GM before reset (and has cookie) => GM too

(comment deftype MyMemoryStore [session-map] ;; was here for testing/experiments only
  SessionStore
  (read-session [_ key]
     (println "Reading session" key "from" session-map "=>" (@session-map key))
    (@session-map key))
  (write-session [_ key data]
    (let [key (or key (str (UUID/randomUUID)))]
      (swap! session-map assoc key data)
      (println "Written session " key "->" data " to " session-map)
      key))
  (delete-session [_ key]
    (swap! session-map dissoc key)
    nil))

(defn init
  "Init the current system, optionally with a default system, returning a Ring handler"
  ([] (init nil))
  ([defaults]
     (let [system (system defaults)]
       (swap! current-system-for-repl (constantly system))
       (-> (handler/site (make-app-routes system))
           (wrap-session {:store
                          ;; (MyMemoryStore. tmp-sess-atm) ; TODO remove - fro troubleshooting only
                          (memory-store (:session-atom system))})
           (wrap-bootstrap-resources)
           (wrap-file-info)))))

(def app (init))

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
  @current-system-for-repl
  @tmp-sess-atm
  )

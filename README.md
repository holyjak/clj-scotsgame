# clj-gamification

Scotsgame: A little game for a workshop on Gamification.

## About

An interactive webapp/game for mobile devices. The first one who access the webapp becomes "gamemaster" and controls transitions between stages of the game and thus what is shown on a projector (showing content of `/projector`) and what other participants can see. The main stages of the "game" are:

1. Team registration - create and register a team and its gamification idea
2. Voting - vote for the team/idea you like most
3. Show results of voting

It uses websockets/long-polling to update the projector when the stage changes.

## Prerequisites

You will need [Leiningen][1] 2 or above and [Heroku toolbelt][2] installed.

[1]: https://github.com/technomancy/leiningen
[2]: https://toolbelt.heroku.com/

## Running

To start a web server for the application, run:

    lein run 5000

To run it in development mode, without support for long-polling but with live reloading, use ring:

    lein ring server

## License

Copyright © 2013 Jakub Holy

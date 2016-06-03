# wordbots

Bots, mostly for slack room.

This project uses [Boot](http://boot-clj.com/).

## Usage

To run the server locally:

    boot repl

Then, in the REPL:

    (require 'wordbots.handler)
    (in-ns 'wordbots.handler)
    (init)
    (def server (run-jetty #'app {:port 4000 :join? false}))

## License

Copyright © 2016 Joshua Davey

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

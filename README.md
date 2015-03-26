# cprop

where all configuration properties converge

![](https://clojars.org/cprop/latest-version.svg)

## Why

there are several env/config ways, libraries. 

* some are based on ENV variables being exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)

## What does it do?

cprop looks for a `conf` var, that is a path to a config file, and edn/reads all the properties from there.

## How

###Letting cprop know where to look

`conf` is just a system property, there are several way it can be set, here are a couple of `dash dee` examples:

####command line:

```clojure
java -jar whatsapp.jar -Dconf="../somepath/whatsapp.conf"
```

####lein

```clojure
:profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}}
```

###Using properties

Let's say a config file is:

```clojure
{:datamic 
    {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}
 :source
    {:account
        {:rabbit
           {:host "127.0.0.1"
            :port 5672
            :vhost "/z-broker"
            :username "guest"
            :password "guest"}}}
 :answer 42}
```

After cprop reads this, it has all of the properties available via a `conf` function:

```clojure
(:require [cprop :refer [conf])
```
```clojure
(conf :answer) ;; 42

(conf :source :account :rabbit :vhost) ;; "/z-broker"
```

###Cursors

It would be somewhat inconvenient to repeat `:source :account :rabbit :vhost` over in over in different pieces of the code that need rabbit values.

That's where the cursors help a lot:

```clojure
(:require [cprop :refer [cursor])
```
```clojure
(def rabbit 
  (cursor :source :account :rabbit))

(rabbit :vhost) ;; "/z-broker"
```

much better.

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

# cprop

where all configuration properties converge

![](https://clojars.org/cprop/latest-version.svg)

## Why

there are several env/config ways, libraries. 

* some are based on ENV variables being exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)
* some allow _only_ string values: no data structures, no numbers, no functions, etc.? (I love my data structures and the power of EDN)

## What does it do?

cprop looks for a `conf` var that is a path to a config file, or a "path" provided at runtime, edn/reads all the properties from there, and makes it available via a `conf` function.

## Letting cprop know where to look

### System property

If no "path" is provided at runtime, cprop will look for a `conf` system property, there are several ways it can be set, here are a couple of `dash dee` examples:

####command line

```clojure
java -jar whatsapp.jar -Dconf="../somepath/whatsapp.conf"
```

####lein

```clojure
:profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}}
```

In order to read a config based on `conf` system property just load it by:

```clojure
(:require [cprop :refer [load-config])

(load-config)
```

check out [cprop test](https://github.com/tolitius/cprop/blob/master/test/cprop/core_test.clj#L5) to see it in action

### Runtime "path"

The above example relies on a `conf` system property to specify a path to the configuration file. In case this path is available at runtime, and/or using a system property is not an option, the path can be provided to `load-config` which will take precedence (`conf`, if set, will be ignored):

```clojure
(load-config path)
```

## Using properties

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

In case the _whole_ config is needed, `conf` is a just function:

```clojure
user=> (conf)

{:datomic
 {:url
  "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
 :source
 {:account
  {:rabbit
   {:host "127.0.0.1",
    :port 5672,
    :vhost "/z-broker",
    :username "guest",
    :password "guest"}}},
 :answer 42}
```


## Cursors

It would be somewhat inconvenient to repeat `:source :account :rabbit :vhost` over and over in different pieces of the code that need rabbit values.

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

### Composable Cursors

In case you pass a cursor somewhere, you can still build new cursors out of it by simply _composing_ them.

working with the same config as in the example above:

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

creating a simple cursor to source:

```clojure
user=> (def src (cursor :source))
#'user/src
user=> (src)
{:account {:rabbit {:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}}}

user=> (src :account)
{:rabbit {:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}}
```

now an `account` cursor can be created out of the `src` one as:

```clojure
user=> (def account (cursor src :account))
#'user/account

user=> (account :rabbit)
{:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}
```

or any nested cursor for that matter:

```clojure
user=> (def rabbit (cursor src :account :rabbit))
#'user/rabbit

user=> (rabbit :host)
"127.0.0.1"
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

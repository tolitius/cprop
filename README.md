# cprop

where all configuration properties converge

[![Clojars Project](https://clojars.org/cprop/latest-version.svg)](http://clojars.org/cprop)

- [Why](#why)
- [What does cprop do?](#what-does-cprop-do)
- [Loading Config](#loading-config)
  - [Default](#default)
  - [Loading from "The Source"](#loading-from-the-source)
- [Using properties](#using-properties)
- [Merging Configurations](#merging-configurations)
  - [Merging with all System and ENV](#merging-with-all-system-and-env)
- [Merging with system properties](#merging-with-system-properties)
  - [System properties cprop syntax](#system-properties-cprop-syntax)
- [Merging with ENV variables](#merging-with-env-variables)
  - [Default](#default-1)
  - [Speaking ENV variables](#speaking-env-variables)
    - [Structure and keywords](#structure-and-keywords)
    - [Types](#types)
  - [Merging ENV example](#merging-env-example)
- [Cursors](#cursors)
  - [Composable Cursors](#composable-cursors)
- [Tips](#tips)
  - [Setting the "conf" system property](#setting-the-conf-system-property)
  - [See what properties were substituted](#see-what-properties-were-substituted)

## Why

there are several env/config ways, libraries. 

* some are _solely_ based on ENV variables exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)
* some allow _only_ string values: no data structures, no numbers, etc.? (I love my data structures and the power of EDN)
* some allow no structure / hierarchy, just one (top) level pile of properties
* some keep a global internal config state, which makes it hard to have app (sub) modules with separate configs

## What does cprop do?

* loads an [EDN](https://github.com/edn-format/edn) config from a classpath and/or file system 
* merges it with system proppertis and ENV variables + the optional merge from sources (file, db, mqtt, http, etc.)
* returns an (immutable) map
* while keeping _no internal state_ => different configs could be used within the same app, i.e. for app sub modules

## Loading Config

```clojure
(require '[cprop.core :refer [load-config]])

(load-config)
```

done.

### Default

By default `cprop` would look in two places for configuration files:

* classpath: for the `config.edn` resource
* file system: for a path identified by the `conf` system property

If both are there, they will be merged. A `file system` source would override matching properties from a `classpath` source,
and the result will be [merged with System properties](README.md#merging-with-system-properties)
and then [merged with ENV variables](README.md#merging-with-env-variables)
for all the _matching_ properties.

check out [cprop test](test/cprop/test/core.clj) to see `(load-config)` in action.

### Loading from "The Source"

`(load-config)` optionaly takes `:resource` and `:file` paths that would override the above defaults.

```clojure
(load-config :resource "path/within/classpath/to-some.edn")
```

```clojure
(load-config :file "/path/to/another.edn")
```

they can be combined:

```clojure
(load-config :resource "path/within/classpath/to-some.edn"
             :file "/path/to/another.edn")
```

as in the case with defaults, file system properties would override matching classpath resource ones.

## Using properties

`(load-config)` function returns a Clojure map, while you can create [cursors](README.md#cursors), working with a config is no different than just working with a map:

```clojure
{:datomic 
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

```clojure
(require '[cprop.core :refer [load-config]])
```
```clojure
(def conf (load-config))

(conf :answer) ;; 42

(get-in conf [:source :account :rabbit :vhost]) ;; "/z-broker"
```

## Merging Configurations

By default `cprop` will merge all configurations it can find in the following order:

1. classpath resource config
2. file on a file system (pointed by a `conf` system property or by `(load-config :file <path>)`)
3. custom configurations, maps from various sources, etc.
4. System properties
5. ENV variables

`#1` and `#2` are going to always be merged by default.

For `#3` `(load-config)` optionally takes a sequence of maps (via `:merge`) that will be merged _after_ the defaults and in the specified sequence:

```clojure
(load-config :merge [{:datomic {:url "foo.bar"}} 
                     {:some {:other {:property :to-merge}}}])
```

this will merge default configurations from a classpath and a file system with the two maps in `:merge` that would override the values that match the existing ones in the configuraion.

Since `:merge` just takes maps it is quite flexible:

```clojure
(require '[cprop.source :refer [from-file
                                from-resource]])
```

```clojure
(load-config :merge [{:datomic {:url "foo.bar"}} 
                     (from-file "/path/to/another.edn")
                     (from-resource "path/within/classpath/to.edn")
                     {:datomic {:url "this.will.win"}} ])
```

in this case the `datomic url` will be overwritten with `"this.will.win"`, since this is the value the last map has.
And notice the "sources", they would just return maps as well.

And of course `:merge` well composes with `:resource` and `:file`:

```clojure
(load-config :resource "path/within/classpath/to.edn"
             :file "/path/to/some.edn"
             :merge [{:datomic {:url "foo.bar"}} 
                     (from-file "/path/to/another.edn")
                     (from-resource "path/within/classpath/to-another.edn")
                     (parse-runtime-args ...)])
```

### Merging with all System and ENV

By default only _matching_ configuration properties will be overridden with the ones from system or ENV.
In case all the system properties or ENV variables are needed (i.e. to add / override something that does not exist in the config),
it can be done with `:merge` as well, since it does a "deep merge" (merges all the nested structures as well):

```clojure
(require '[cprop.source :refer [from-system-props
                                from-env]])
```

`(from-system-props)` returns a map of ALL system properties that is ready to be merged with the config
`(from-env)` returns a map of ALL ENV variables that is ready to be merged with the config

one or both can be used:

```clojure
(load-config :merge [(from-system-props)])
```

```clojure
(load-config :merge [(from-system-props)
                     (from-env)])
```

Everything of course composes together if needed:

```clojure
(load-config :resource "path/within/classpath/to.edn"
             :file "/path/to/some.edn"
             :merge [{:datomic {:url "foo.bar"}} 
                     (from-file "/path/to/another.edn")
                     (from-resource "path/within/classpath/to-another.edn")
                     (parse-runtime-args ...)
                     (from-system-props)
                     (from-env)])
```

It can get as creative as needed, but.. _this should cover most cases_:

```clojure
(load-config)
```

## Merging with system properties

By default cprop will merge all configurations with system properties that match the ones that are there in configs (i.e. intersection).
In case ALL system properties need to be merged (i.e. union), this can be done with `:merge`:


```clojure
(require '[cprop.source :refer [from-system-props]])

(load-config :merge [(from-system-props)])
```

`(from-system-props)` returns a map of ALL system properties that is ready to be merged with the config.

### System properties cprop syntax

System properties are usually separated by `.` (periods). cprop will convert these periods to `-` (dashes).

In order to override a nested property use `_` (underscode).

Here is an example. Let say we have a config:

```clojure
{:http
 {:pool
  {:socket-timeout 600000,
   :conn-timeout 60000,
   :conn-req-timeout 600000,
   :max-total 200,
   :max-per-route 10}}}
```

a system property `http_pool_socket.timeout` would point to a `{:http {:pool {:socket-timeout value}}}`. So to change a value it can be set as:

```bash
-Dhttp_pool_socket.timeout=4242
```

or

```java
System.setProperty("http_pool_socket.timeout" "4242");
```

## Merging with ENV variables

Production environments are full of "secrets", could be passwords, URLs, ports, keys, etc.. Which are better driven by the ENV variables rather than being hardcoded in the config file.

12 factor [config section](http://12factor.net/config) mentions that:

> The twelve-factor app stores config in environment variables

While not _everything_ needs to live in environment variables + config files are a lot easier to visualize and develop with, this is a good point 12 factor makes:

> A litmus test for whether an app has all config correctly factored out of the code is whether the codebase could be made open source at any moment, without compromising any credentials.

Hence it makes a lot of sense for `cprop` to merge the config file with ENV variables when `(load-config)` is called.

### Default

By default cprop will merge all configurations with ENV variables that match the ones that are there in configs (i.e. intersection).
In case ALL ENV variables need to be merged (i.e. union), this can be done with `:merge`:

```clojure
(require '[cprop.source :refer [from-env]])

(load-config :merge [(from-env)])
```

`(from-env)` returns a map of ALL environment variables that is ready to be merged with the config.

### Speaking ENV variables

#### Structure and keywords

ENV variables lack structure. The only way to mimic the structure is via use of an underscore character.
The `_` is converted to `-` by cprop, so instead, to identify nesting, two underscores can be used.

For example to override a socket timeout in a form of:

```clojure
{:http
 {:pool
  {:socket-timeout 600000}}}
```

```bash
export HTTP__POOL__SOCKET_TIMEOUT=4242
```

Notice how two underscores are used for "getting in" and a single underscore just gets converted to a dash to match the keyword.

#### Types

ENV variables, when read by [(System/getenv)](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#getenv--) are all _strings_.

cprop will convert these strings to datatypes. e.g.:

```bash
export APP_HTTP_PORT=4242                 # would be a Long
export APP_DB_URL=jdbc:sqlite:order.db    # would be a String
export APP_DB_URL='jdbc:sqlite:order.db'  # would be a String
export APP_DB_URL="jdbc:sqlite:order.db"  # would be a String
export APP_NUMS='[1 2 3 4]'               # would be an EDN data structure (i.e. a vector in this example)
```

A small caveat is _purely numeric_ strings. For example:

```bash
export BAD_PASSWORD='123456789'           # would still be a number (i.e. Long)
```

in order to make it really a String, double quotes will help:

```bash
export BAD_PASSWORD='"123456789"'         # would be a String
```

### Merging ENV example

Let's say we have a config file that needs values to be complete:

```clojure
{:datomic {:url "CHANGE ME"},
 :aws
 {:access-key "AND ME",
  :secret-key "ME TOO",
  :region "FILL ME IN AS WELL",
  :visiblity-timeout-sec 30,
  :max-conn 50,
  :queue "cprop-dev"},
 :io
 {:http
  {:pool
   {:socket-timeout 600000,
    :conn-timeout :I-SHOULD-BE-A-NUMBER,
    :conn-req-timeout 600000,
    :max-total 200,
    :max-per-route :ME-ALSO}}},
 :other-things
 ["I am a vector and also like to play the substitute game"]}
```

In order to fill out all the missing pieces we can export ENV variables as:

```bash
export AWS__ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
export AWS__SECRET_KEY="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
export AWS__REGION='us-east-1'
export IO__HTTP__POOL__CONN_TIMEOUT=60000
export IO__HTTP__POOL__MAX_PER_ROUTE=10
export OTHER__THINGS='[1 2 3 "42"]'
```

_(all the 3 versions of AWS values will be Strings, different ways are here just as an example)_

Now whenever the config is loaded with `(load-config)` cprop will find these ENV variables and will merge them
with the original config file into one complete configuration:

```clojure
user=> (load-config)
substituting [:aws :region] with a ENV/system.property specific value
substituting [:aws :secret-key] with a ENV/system.property specific value
substituting [:io :http :pool :conn-timeout] with a ENV/system.property specific value
substituting [:io :http :pool :max-per-route] with a ENV/system.property specific value
substituting [:datomic :url] with a ENV/system.property specific value
substituting [:aws :access-key] with a ENV/system.property specific value
substituting [:other-things] with a ENV/system.property specific value
{:datomic
 {:url
  "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
 :aws
 {:access-key "AKIAIOSFODNN7EXAMPLE",
  :secret-key "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
  :region "us-east-1",
  :visiblity-timeout-sec 30,
  :max-conn 50,
  :queue "cprop-dev"},
 :io
 {:http
  {:pool
   {:socket-timeout 600000,
    :conn-timeout 60000,
    :conn-req-timeout 600000,
    :max-total 200,
    :max-per-route 10}}},
 :other-things [1 2 3 "42"]}
```

notice that `cprop` also tells you wnenever a property is substituted.

## Cursors

It would be somewhat inconvenient to repeat `[:source :account :rabbit :prop]` over and over in different pieces of the code that need rabbit values.

That's where the cursors help a lot:

```clojure
(require '[cprop.core :refer [load-config cursor]])
```
```clojure
(def conf (load-config))

(def rabbit 
  (cursor conf :source :account :rabbit))

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
user=> (def src (cursor conf :source))
#'user/src
user=> (src)
{:account {:rabbit {:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}}}

user=> (src :account)
{:rabbit {:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}}
```

now an `account` cursor can be created out of the `src` one as:

```clojure
user=> (def account (cursor conf src :account))
#'user/account

user=> (account :rabbit)
{:host "127.0.0.1", :port 5672, :vhost "/z-broker", :username "guest", :password "guest"}
```

or any nested cursor for that matter:

```clojure
user=> (def rabbit (cursor conf src :account :rabbit))
#'user/rabbit

user=> (rabbit :host)
"127.0.0.1"
```

## Tips

### Setting the "conf" system property

There are several ways the `conf` property can be set:

####command line

```clojure
java -jar whatsapp.jar -Dconf="../somepath/whatsapp.conf"
```

####boot

```clojure
(System/setProperty "conf" "resources/config.edn")
```

####lein

```clojure
:profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}}
```

### See what properties were substituted

In order to see which properties were substituted by the cprop merge, export a DEBUG environment variable to `y` / `Y`:

```bash
export DEBUG=y
```

if this variable is exported, cprop won't keep substitutions a secret:

```clojure
user=> (load-config)
substituting [:aws :region] with a ENV/system.property specific value
substituting [:aws :secret-key] with a ENV/system.property specific value
substituting [:io :http :pool :conn-timeout] with a ENV/system.property specific value
substituting [:io :http :pool :max-per-route] with a ENV/system.property specific value
substituting [:datomic :url] with a ENV/system.property specific value
substituting [:aws :access-key] with a ENV/system.property specific value
substituting [:other-things] with a ENV/system.property specific value
;; ...
```

#### Why not default?

The reason this is not on by default is merging ALL env and/or system properties with configs
which is quite noisy and not very useful (i.e. can be hundreds of entries..).

## License

Copyright © 2016 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

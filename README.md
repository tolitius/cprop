# cprop

where all configuration properties converge

[![<! build-status](https://travis-ci.org/tolitius/cprop.svg?branch=master)](https://travis-ci.org/tolitius/cprop)
[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Fcprop%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/cprop/releases)
[![<! clojars](https://img.shields.io/clojars/v/cprop.svg)](https://clojars.org/cprop)

###### _any_ questions or feedback: [clojurians slack](https://clojurians.slack.com/messages/clojure/) <img src="doc/img/slack-icon.png" width="15px"> (or just [open an issue](https://github.com/tolitius/cprop/issues))

- [Why](#why)
- [What does cprop do?](#what-does-cprop-do)
- [Loading Config](#loading-config)
  - [Default](#default)
  - [Loading from "The Source"](#loading-from-the-source)
- [Using properties](#using-properties)
- [Merging Configurations](#merging-configurations)
  - [Merging with all System and ENV](#merging-with-all-system-and-env)
  - [Override all configs](#override-all-configs)  
- [Merging with system properties](#merging-with-system-properties)
  - [System properties cprop syntax](#system-properties-cprop-syntax)
- [Merging with ENV variables](#merging-with-env-variables)
  - [Default](#default-1)
  - [Speaking ENV variables](#speaking-env-variables)
    - [Structure and keywords](#structure-and-keywords)
    - [Types](#types)
  - [Merging ENV example](#merging-env-example)
- [Merging with property files](#merging-with-property-files)
  - [Property files syntax](#property-files-syntax)
- [Read "as is" (not EDN)](#read-as-is-not-edn)
- [Cursors](#cursors)
  - [Composable Cursors](#composable-cursors)
- [Tools](#tools)
  - [Translating EDN](#translating-edn)
    - [EDN to .properties](#edn-to-properties)
    - [EDN to .env](#edn-to-env)
- [Tips](#tips)
  - [Setting the "conf" system property](#setting-the-conf-system-property)
  - [See which files were loaded and what properties were substituted](#see-which-files-were-loaded-and-what-properties-were-substituted)
  - [Convert properties to one level map](#convert-properties-to-a-one-level-map)

## Why

there are several env/config ways, libraries.

* some are _solely_ based on ENV variables exported as individual properties: 100 properties? 100 env variables exported..
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)
* some allow _only_ string values: no data structures, no numbers, etc.? (I love my data structures and the power of EDN)
* some allow no structure / hierarchy, just one (top) level pile of properties
* some keep a global internal config state, which makes it hard to have app (sub) modules with separate configs

## What does cprop do?

* loads an [EDN](https://github.com/edn-format/edn) config from a classpath and/or file system
* merges it with system properties and ENV variables + the optional merge from sources (file, db, mqtt, http, etc.)
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

check out [cprop test](test/cprop/test/core.cljc) to see `(load-config)` in action.

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
                     (from-props-file "/path/to/some.properties")
                     (from-system-props)
                     (from-env)])
```

It can get as creative as needed, but.. _this should cover most cases_:

```clojure
(load-config)
```

### Override all configs

cprop merges _matching_ properties and ENV variables by default. In order to override that, or any other configs, properties or ENV variables `load-congfig` function takes an optional `:override-with` argument with a map that will override any matching (top level or however deeply nested) properties. If provided, this would be the last merge step applied after "all":

```bash
$ export DATOMIC__URL=foo
```

```clojure
=> (load-config)
{:datomic {:url "foo"},
 :source ... }
```

but could be overriden with:

```clojure
=> (load-config :override-with {:datomic {:url "bar"}})
{:datomic {:url "bar"},
 :source ... }
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

## Merging with property files

It is important to be able to integrate with existing Java applications or simply with configurations that are done as `.properties` files, i.e. not EDN.

`cprop` can easily convert `.properties` files into EDN maps and merge it on top of the existing configuration by using `(from-props-file path)` function. Here is an example:

```clojure
(require '[cprop.source :refer [from-props-file]])

(load-config :merge [(from-props-file "path-to/overrides.properties")])
```

Which would merge:

* `config.edn` as a classpath resource
* with matching system properties
* with matching ENV variables
* with "path-to/overrides.properties" file

Here is an example. Let's say we have this config:

```clojure
{:datomic {:url "CHANGE ME"}

 :aws {:access-key "AND ME"
       :secret-key "ME TOO"
       :region "FILL ME IN AS WELL"
       :visiblity-timeout-sec 30
       :max-conn 50
       :queue "cprop-dev"}

  :io {:http {:pool {:socket-timeout 600000
                     :conn-timeout :I-SHOULD-BE-A-NUMBER
                     :conn-req-timeout 600000
                     :max-total 200
                     :max-per-route :ME-ALSO}}}

  :other-things ["I am a vector and also like to place the substitute game"]}
```

and this `overrides.properties` file:

```properties
datomic.url=datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic

source.account.rabbit.host=localhost

aws.access-key=super secret key
aws.secret_key=super secret s3cr3t!!!
aws.region=us-east-2

io.http.pool.conn_timeout=42
io.http.pool.max_per_route=42

other_things=1,2,3,4,5,6,7
```

We can apply the overrides with cprop as:

```clojure
(load-config :merge [(from-props-file "overrides.properties")])
```

which will merge them and will return:

```clojure
{:datomic
 {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
 :aws
 {:access-key "super secret key",
  :secret-key "super secret s3cr3t!!!",
  :region "us-east-2",
  :visiblity-timeout-sec 30,
  :max-conn 50,
  :queue "cprop-dev"},
 :io
 {:http
  {:pool
   {:socket-timeout 600000,
    :conn-timeout 42,
    :conn-req-timeout 600000,
    :max-total 200,
    :max-per-route 42}}},
 :other-things ["1" "2" "3" "4" "5" "6" "7"]}
```

### Property files syntax

The traditional syntax of a `.properties` file does not change. For example:

* `.` means structure

`four.two=42` would be translated to `{:four {:two 42}}`

* `_` would be a key separator

`fourty_two=42` would be translated to `{:fourty-two 42}`

* `,` in a value would be seq separator

`planet.uran.moons=titania,oberon` would be translated to `{:planet {:uran {:moons ["titania" "oberon"]}}}`

For example let's take a `solar-system.properties` file:

```properties
## solar system components
components=sun,planets,dwarf planets,moons,comets,asteroids,meteoroids,dust,atomic particles,electromagnetic.radiation,magnetic field

star=sun

## planets with Earth days to complete an orbit
planet.mercury.orbit_days=87.969
planet.venus.orbit_days=224.7
planet.earth.orbit_days=365.2564
planet.mars.orbit_days=686.93
planet.jupiter.orbit_days=4332.59
planet.saturn.orbit_days=10755.7
planet.uran.orbit_days=30688.5
planet.neptune.orbit_days=60148.35

## planets natural satellites
planet.earth.moons=moon
planet.jupiter.moons=io,europa,ganymede,callisto
planet.saturn.moons=titan
planet.uran.moons=titania,oberon
planet.neptune.moons=triton

# favorite dwarf planet's moons
dwarf.pluto.moons=charon,styx,nix,kerberos,hydra
```

```clojure
(from-props-file "solar-system.properties")
```

will convert it to:

```clojure
{:components ["sun" "planets" "dwarf planets" "moons" "comets"
              "asteroids" "meteoroids" "dust" "atomic particles"
              "electromagnetic.radiation" "magnetic field"],
 :star "sun",
 :planet
 {:uran {:moons ["titania" "oberon"],
         :orbit-days 30688.5},
  :saturn {:orbit-days 10755.7,
           :moons "titan"},
  :earth {:orbit-days 365.2564,
          :moons "moon"},
  :neptune {:moons "triton",
            :orbit-days 60148.35},
  :jupiter {:moons ["io" "europa" "ganymede" "callisto"],
            :orbit-days 4332.59},
  :mercury {:orbit-days 87.969},
  :mars {:orbit-days 686.93},
  :venus {:orbit-days 224.7}},
 :dwarf {:pluto {:moons ["charon" "styx" "nix" "kerberos" "hydra"]}}}
```

## Read "as is" (not EDN)

Not all configs and properties come in EDN format, and some of these not EDN properties / env variables can't be read with Clojure's EDN reader, for example:

```clojure
=> (require '[clojure.edn :as edn])

=> (edn/read-string "7 Nov 22:44:53 2015")
7
```
also:
```clojure
boot.user=> (edn/read-string "7Nov 22:44:53 2015")
java.lang.NumberFormatException: Invalid number: 7Nov
```

and imagine if this `7 Nov 22:44:53 2015` is an ENV variable that you can't change, but still need to be able to use. For cases like these you can use an `:as-is` flag to communicate to cprop to treat properties/vars "as is", in other words take them as they come and don't try to convert them into anything.

`:as-is?` optional param is available on all the source (`from-env`, `from-system-props`, `from-props-file`, etc.) functions which will read props/vars as is:

```bash
$ export FOO='"4242"'
$ export BAR=4242
$ export DATE='7 Nov 22:44:53 2015'
$ export VEC='[1 2 3 4]'
```
```clojure
=> (require '[cprop.source :as s])

=> (:foo (s/from-env))
"4242"
=> (:bar (s/from-env))
4242
=> (:date (s/from-env))
7                                     ;; uh.. that's bad
=> (:vec (s/from-env))
[1 2 3 4]
```
but
```clojure
=> (:foo (s/from-env {:as-is? true}))
"\"4242\""
=> (:bar (s/from-env {:as-is? true}))
"4242"
=> (:date (s/from-env {:as-is? true}))
"7 Nov 22:44:53 2015"                  ;; that's good
=> (:vec (s/from-env {:as-is? true}))
"[1 2 3 4]"
```

If you need _ALL_ the properties and configs to come in "as is" (not as EDN) `:as-is` flag is also available at the top level:

```clojure
(load-config :as-is? true)
```

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

## Tools

### Translating EDN

Depending on the build infrastructure, continuous integration, deployments, some environments would require `.properties` files with overrides instead of EDN configs or ENV variable overrides.

Also it's easier to use EDN file with overrides in development before converting them to a set of ENV variables, but it can take some time, and would somewhat error prone, to convert this EDN file with overrides to a set of ENV variables.

For those cases above cprop has helper tools that can help translating from EDN.

Let's use [this config file](dev-resources/config.edn) an example:

```clojure
boot.user=> (pprint config)
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

#### EDN to .properties

We can pass this config to a `map->props-file` function that will convert it to a `.properties` formatted file:

```clojure
(require '[cprop.tools :as t])
```

```clojure
(t/map->props-file config)
"/tmp/cprops-1475854845508-538388633502378948.tmp"
```

it returns a path to a file it created, which we can look at:

```clojure
(print (slurp "/tmp/cprops-1475854845508-538388633502378948.tmp"))

answer=42
source.account.rabbit.host=127.0.0.1
source.account.rabbit.port=5672
source.account.rabbit.vhost=/z-broker
source.account.rabbit.username=guest
source.account.rabbit.password=guest
datomic.url=datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic
```

#### EDN to .env

We can pass this config to a `map->env-file` function that will convert it to a `.env` formatted file:

```clojure
(require '[cprop.tools :as t])
```

```clojure
(t/map->env-file config)
"/tmp/cprops-1475854874506-8756956459082793585.tmp"
```

it returns a path to a file it created, which we can look at:

```clojure
(print (slurp "/tmp/cprops-1475854874506-8756956459082793585.tmp"))

export ANSWER=42
export SOURCE__ACCOUNT__RABBIT__HOST=127.0.0.1
export SOURCE__ACCOUNT__RABBIT__PORT=5672
export SOURCE__ACCOUNT__RABBIT__VHOST=/z-broker
export SOURCE__ACCOUNT__RABBIT__USERNAME=guest
export SOURCE__ACCOUNT__RABBIT__PASSWORD=guest
export DATOMIC__URL=datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic
```

## Tips

### Setting the "conf" system property

There are several ways the `conf` property can be set:

#### command line

```clojure
java -Dconf="../somepath/whatsapp.conf" -jar whatsapp.jar
```

#### boot

```clojure
(System/setProperty "conf" "resources/config.edn")
```

#### lein

```clojure
:profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}}
```

### See which files were loaded and what properties were substituted

In order to see which files were read (and merged) and which properties were substituted by the cprop merge,
export a DEBUG environment variable to `y` / `Y`:

```bash
export DEBUG=y
```

if this variable is exported, cprop won't keep files and substitutions a secret:

```clojure
user=> (load-config)
read config from stream: "dev-resources/config.edn"    ;;
read config from file: "dev-resources/config.edn"      ;; => a sample output
read config from resource: "config.edn"                ;;

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

### Convert properties to a "one level" map

Besides the `from-props-file` function that converts `.properties` file to a map _with hierarchy_, there is also a `slurp-props-file` function that simply converts a property file to a map without parsing values or building a hierarchy:

```clojure
(require '[cprop.source :refer [slurp-props-file]])

(slurp-props-file "solar-system.properties")
```

```properties
{"star" "sun",

 "planet.jupiter.moons" "io,europa,ganymede,callisto",
 "planet.neptune.moons" "triton",
 "planet.jupiter.orbit_days" "4332.59",
 "planet.uran.orbit_days" "30688.5",
 "planet.venus.orbit_days" "224.7",
 "planet.earth.moons" "moon",
 "planet.saturn.orbit_days" "10755.7",
 "planet.mercury.orbit_days" "87.969",
 "planet.saturn.moons" "titan",
 "planet.earth.orbit_days" "365.2564",
 "planet.uran.moons" "titania,oberon",
 "planet.mars.orbit_days" "686.93",
 "planet.neptune.orbit_days" "60148.35"

 "dwarf.pluto.moons" "charon,styx,nix,kerberos,hydra",

 "components" "sun,planets,dwarf planets,moons,comets,asteroids,meteoroids,dust,atomic particles,electromagnetic.radiation,magnetic field"}
```

## License

Copyright © 2019 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

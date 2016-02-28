# cprop

where all configuration properties converge

![](https://clojars.org/cprop/latest-version.svg)

- [Why](#why)
- [What does cprop do?](#what-does-cprop-do)
- [Loading Config](#loading-config)
  - [Default](#default)
  - [Loading from "The Source"](#loading-from-the-source)
- [Using properties](#using-properties)
- [Merging with ENV variables](#merging-with-env-variables)
  - [Speaking ENV variables](#speaking-env-variables)
    - [Structure](#structure)
    - [Types](#types)
    - [Keywords](#keywords)
  - [Taming ENV variables](#taming-env-variables)
    - [Structure](#structure-1)
    - [Types](#types-1)
    - [Keywords](#keywords-1)
  - [Merging ENV example](#merging-env-example)
- [Cursors](#cursors)
  - [Composable Cursors](#composable-cursors)

## Why

there are several env/config ways, libraries. 

* some are _solely_ based on ENV variables exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)
* some allow _only_ string values: no data structures, no numbers, etc.? (I love my data structures and the power of EDN)
* some keep a global internal config state, which makes it hard to have app (sub) modules with separate configs

## What does cprop do?

* loads an [EDN](https://github.com/edn-format/edn) config from a given source (file, db, mqtt, etc.)
* merges it with ENV variables / system properties + optional merge sources / configs
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

If both are there, they will be merged with file system overriding matching classpath properties.

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

check out [cprop test](test/cprop/test/core.clj) to see `(load-config)` in action

### Loading from "The Source"

`load-config` optionaly takes `:resource` and `:file` paths that would override the above defaults.

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

* classpath resource config
* file on a file system (pointed by a `conf` system property or by `(load-config :file <path>)`)
* custom configurations, maps from various sources, etc.
* ENV variables

Classpath (`resource`) and file system (`conf` / `:file`) are going to always be merged by default.

Optionally `load-config` takes a `:merge` sequence of maps that will be merged after the defaults in the specified sequence:

```clojure
(load-config :merge [{:datomic {:url "foo.bar"}} 
                     {:some {:other {:property :to-merge}}}])
```

this will merge default configurations from a classpath and a file system with the two maps in `:merge` that would overwrite the values that match the existing ones in the configuraion.

Since `:merge` just takes maps it is quite felxible:

```clojure
(require '[cprop.source :refer [from-file from-resource]])
```

```clojure
(load-config :merge [{:datomic {:url "foo.bar"}} 
                     (from-file "/path/to/another.edn")
                     (from-resource "path/within/classpath/to.edn")
                     {:datomic {:url "this.will.win"}} ])
```

in this case datomic url will be overwritten with `"this.will.win"`, since this is what the last map has. And notice the "sources", they would just return maps as well.

And of course `:merge` well composes with `:resource` and `:file`:

```clojure
(load-config :resource "path/within/classpath/to.edn"
             :file "/path/to/some.edn"
             :merge [{:datomic {:url "foo.bar"}} 
                     (from-file "/path/to/another.edn")
                     (from-resource "path/within/classpath/to-another.edn")
                     (parse-runtime-args ...)])
```

It can get as creative as needed, but.. _this should cover most cases_:

```clojure
(load-config)
```

The last merge that occurs is with ENV variables that deserves its own [detailed section](README.md#merging-with-env-variables) of the docs.

## Merging with ENV variables

Production environments are full of "secrets", could be passwords, URLs, ports, keys, etc.. Which are better drived by the ENV variables rather than being hardcoded in the config file.

12 factor [config section](http://12factor.net/config) mentions that:

> The twelve-factor app stores config in environment variables

While not _everything_ needs to live in environment variables + config files are a lot easier to visualize and develop with, this is a good point 12 factor makes:

> A litmus test for whether an app has all config correctly factored out of the code is whether the codebase could be made open source at any moment, without compromising any credentials.

Hence it makes a lot of sense for `cprop` to merge the config file with ENV variables when `(load-config)` or `(load-config path)` is called.

### Speaking ENV variables

#### Structure

ENV variables lack structure. The only way to mimic the structire is via use of an underscope character. For example to express a datomic url:

```clojure
{:datomic
 {:url ... }}
```

ENV variable would have them separated by `_`:

```bash
export DATOMIC_URL=...
```

#### Types

Also ENV variables, when read by [(System/getenv)](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#getenv--) are all _strings_, hence.. no types, no data structures.

```clojure
{:http
 {:port 4242}}
```

Java reads all these as Strings:

```bash
export SERVER_PORT=4242
```
```bash
export SERVER_PORT='4242'
```
```bash
export SERVER_PORT="4242"
```
```bash
export SERVER_PORT='"4242"'
```

#### Keywords

An [EDN](https://github.com/edn-format/edn) config file is usually a map, where keys are not limited to be "single worded". For example:

```clojure
{:http
 {:pool
  {:socket-timeout 600000,
   :conn-timeout 60000,
   :conn-req-timeout 600000,
   :max-total 200,
   :max-per-route 10}}}
```

A `socket-timeout` key has two words in it. It is both readable and it reflects the meaning behind this key a lot better than if it was a single word.

Since ENV variables can only have alphanumerics and `_`, where `_` is already used for structure, it is not clear how to represent `{:http {:pool {:socket-timeout ...}}}` in a single ENV variable.

### Taming ENV variables

#### Structure

To repesent nesting use underscores:

```bash
export IO_HTTP_POOL
```

#### Types

To tame data structures and strings, use single quotes `'`, leave numbers as numbers:

```bash
export AWS_REGION='"ues-east-1"'     ## String
```

```bash
export SERVER_PORT=4242              ## Number
```

```bash
export LUCKY_NUMBERS='[1 2 3 "42"]'  ## Vector (or any other data structure inside single quotes)
```

#### Keywords

Since all we can use is `_` (underscore), use two of them to represent a `-` (dash) in a keyword:

```clojure
{:http
 {:pool
  {:socket-timeout 600000,
   :conn-timeout 60000,
   :conn-req-timeout 600000,
   :max-total 200,
   :max-per-route 10}}}
```

```bash
export IO_HTTP_POOL_CONN__TIMEOUT=60000
export IO_HTTP_POOL_MAX__PER__ROUTE=10
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
 ["I am a vector and also like to place the substitute game"]}
```

In order to fill out all the missing pieces we can export ENV variables as:

```bash
export AWS_ACCESS__KEY='"AKIAIOSFODNN7EXAMPLE"'
export AWS_SECRET__KEY='"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"'
export AWS_REGION='"ues-east-1"'
export IO_HTTP_POOL_CONN__TIMEOUT=60000
export IO_HTTP_POOL_MAX__PER__ROUTE=10
export OTHER__THINGS='[1 2 3 "42"]'
```

Now whenever the config is loaded with `load-config` cprop will find these ENV variables and will merge them with the original config file in to a one complete configuration:

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
  :region "ues-east-1",
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

## License

Copyright © 2016 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

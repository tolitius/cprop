# cprop

where all configuration properties converge

![](https://clojars.org/cprop/latest-version.svg)

- [Why](#why)
- [What does it do?](#what-does-it-do)
- [Letting cprop know where to look](#letting-cprop-know-where-to-look)
  - [System property](#system-property)
    - [command line](#command-line)
    - [lein](#lein)
  - [Runtime "path"](#runtime-path)
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

Now whenever the config is loaded with `(load-config)` or `(load-config path)` cprop will find these ENV variables and will merge them with the original config file in to a one complete configuration:

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

Copyright © 2016 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

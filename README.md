# cprop

where all configuration properties converge

![](https://clojars.org/cprop/latest-version.svg)

## why

there are several env/config ways, libraries. 

* some are based on ENV variables being exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)

## what does it do?

cprop looks for a `conf` var, that is a path to a config file, and edn/reads all the properties from there.

## how

###Dash Dee:

```clojure
java -jar whatsapp.jar -Dconf="resources/conf/whatsapp.conf"
```

###lein

```clojure
:profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}}
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

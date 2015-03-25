# cprop

where all configuration properties converge

## why

there are several env/config ways, libraries. 

* some are based on ENV variables being exported as individual properties: 100 properties? 100 env variables exported.. 
* some rely on a property file within the classpath: all good, but requires wrestling with uberjar (META-INF and friends)

cprop is yet another one. it is looking for a `config.edn` (or `app-name.conf`) var that is just a path to a config file.

## how

###Dash Dee:

```shell
java -jar whatsapp.jar -Dconfig.edn="resources/conf/whatsapp.conf"
```

###lein

```
:profiles {:dev {:jvm-opts ["-Dconfig.edn=resources/config.edn"]}}
```

## License

Copyright © 2015 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

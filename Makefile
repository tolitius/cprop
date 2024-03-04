.PHONY: clean test jar tag outdated install deploy tree repl

clean:
	rm -rf target
	rm -rf classes

jar: clean test tag
	clojure -A:jar

test: clean
	clojure -X:test :patterns '[".*"]'

outdated:
	clojure -M:outdated

tag:
	clojure -A:tag

install: jar
	clojure -A:install

deploy: jar
	clojure -A:deploy

tree:
	mvn dependency:tree

repl:
	clojure -A:dev -A:repl

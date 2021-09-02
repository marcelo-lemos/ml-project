#!/bin/bash

classpath=.:bin:lib/microrts.jar:lib/commons-cli-1.4.jar:lib/log4j-api-2.11.1.jar:lib/strategy-tactics.jar
classpath="$classpath:lib/log4j-core-2.11.1.jar:lib/jdom.jar:lib/ufv.jar:lib/capivara.jar"

echo "Launching experiment..."

java -classpath $classpath -Djava.library.path=lib/ rl.Runner "$@"

echo "Done."

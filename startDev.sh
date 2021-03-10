#!/bin/bash
nodemon -e java -w ./src -x 'mvn clean compile exec:java -Dexec.mainClass=org.fcrepo.camel.ldpath.App'

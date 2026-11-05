#!/bin/sh
set -eu
mkdir -p out
javac -d out src/*.java
java -cp out PropertyStorageExample

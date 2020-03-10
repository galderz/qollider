#!/usr/bin/env bash

set -e

JAVA_VERSION="13.0.2"
JAVA="${JAVA_VERSION}.hs-adpt"
JBANG_VERSION="0.19.0"

getSdkman() {
    curl -s "https://get.sdkman.io" | bash
    source ${HOME}/.sdkman/bin/sdkman-init.sh
}

installJava() {
    n | sdk install java ${JAVA} || true
    sdk use java ${JAVA}
}

installJBang() {
    sdk install jbang ${JBANG_VERSION} || true
    sdk use jbang ${JBANG_VERSION}
    source ${HOME}/.sdkman/bin/sdkman-init.sh
}

installMaven() {
    n | sdk install maven || true
}

run() {
    jbang src/test.java
}

main() {
    getSdkman
    installJava
    installJBang
    installMaven
    run
}

main
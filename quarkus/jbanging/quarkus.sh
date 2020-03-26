#!/usr/bin/env bash

set -e

SCRIPT_JDK_VERSION="14.0.0"
SCRIPT_JDK="${SCRIPT_JDK_VERSION}.hs-adpt"
SCRIPT_JDK_HOME="${HOME}/.sdkman/candidates/java/${SCRIPT_JDK}"

BOOT_JDK_VERSION="11.0.6"
BOOT_JDK="${BOOT_JDK_VERSION}.hs-adpt"
BOOT_JDK_HOME="${HOME}/.sdkman/candidates/java/${BOOT_JDK}"

JBANG_VERSION="0.19.0"

getSdkman() {
    curl -s "https://get.sdkman.io" | bash
    source ${HOME}/.sdkman/bin/sdkman-init.sh
}

installBootJDK() {
    n | sdk install java ${BOOT_JDK} || true
}

installScriptJDK() {
    n | sdk install java ${SCRIPT_JDK} || true
}

installJBang() {
    sdk install jbang ${JBANG_VERSION} || true
    sdk use jbang ${JBANG_VERSION}
}

installMaven() {
    n | sdk install maven || true
}

run() {
    source ${HOME}/.sdkman/bin/sdkman-init.sh
    JAVA_HOME=${SCRIPT_JDK_HOME}
        BOOT_JDK_HOME=${BOOT_JDK_HOME} \
        jbang src/quarkus.java "$@"
}

main() {
    getSdkman
    installBootJDK
    installScriptJDK
    installJBang
    installMaven
    run "$@"
}

main "$@"

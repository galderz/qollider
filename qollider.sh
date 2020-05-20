#!/usr/bin/env bash

set -e

SCRIPT_JDK_VERSION="14.0.1"
SCRIPT_JDK="${SCRIPT_JDK_VERSION}.hs-adpt"
SCRIPT_JDK_HOME="${HOME}/.sdkman/candidates/java/${SCRIPT_JDK}"

BOOT_JDK_VERSION="11.0.7"
BOOT_JDK="${BOOT_JDK_VERSION}.hs-adpt"
BOOT_JDK_HOME="${HOME}/.sdkman/candidates/java/${BOOT_JDK}"

JBANG_VERSION="0.22.0.2"

getSdkman() {
    local file="${HOME}/.sdkman/bin/sdkman-init.sh"
    if [ ! -e "${file}" ]; then
        echo 'Installing sdkman'
        curl -s "https://get.sdkman.io" | bash
    fi
    source "${file}"
}

installBootJDK() {
    if [ ! -d "${BOOT_JDK_HOME}" ]; then
        n | sdk install java ${BOOT_JDK}
    fi
}

installScriptJDK() {
    if [ ! -d "${SCRIPT_JDK_HOME}" ]; then
        n | sdk install java ${SCRIPT_JDK}
    fi
}

installJBang() {
    local directory="${HOME}/.sdkman/candidates/jbang/${JBANG_VERSION}"
    if [ ! -d "${directory}" ]; then
        sdk install jbang ${JBANG_VERSION}
        n | sdk use jbang ${JBANG_VERSION}
    fi
}

installMaven() {
    local directory="${HOME}/.sdkman/candidates/maven"
    if [ ! -d "${directory}" ]; then
        n | sdk install maven || true
    fi
}

run() {
    source ${HOME}/.sdkman/bin/sdkman-init.sh
    JAVA_HOME=${SCRIPT_JDK_HOME}
        BOOT_JDK_HOME=${BOOT_JDK_HOME} \
        jbang src/qollider.java "$@"
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

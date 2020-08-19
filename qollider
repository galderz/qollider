#!/usr/bin/env bash

set -e

Q_HOME="${HOME}/.qollider"

JBANG_HOME="${Q_HOME}/jbang"
JBANG_EXEC="${JBANG_HOME}/bin/jbang"
JBANG_VERSION="0.36.1"

installJBang() {
    local file="${JBANG_EXEC}"
    if [ ! -e "${file}" ]; then
        local url="https://github.com/jbangdev/jbang/releases/download/v${JBANG_VERSION}/jbang-${JBANG_VERSION}.tar"
        local archive="${Q_HOME}/jbang.tar"

        mkdir -p ${JBANG_HOME}

        curl --location ${url} > ${archive}
        tar -xvpf ${archive} -C ${JBANG_HOME} --strip-components 1

        rm -drf ${archive}
    fi
}

run() {
    ${JBANG_EXEC} src/qollider.java "$@"
}

main() {
    mkdir -p ${Q_HOME}
    installJBang
    run "$@"
}

main "$@"

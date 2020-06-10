#!/usr/bin/env bash

set -e

Q_HOME="${HOME}/workspace/qollider"

JAVA_HOME="undefined"
JAVA_VERSION_MAJOR="14"
JAVA_VERSION_BUILD="7"
JAVA_VERSION="${JAVA_VERSION_MAJOR}.0.1"
JAVA_URL_BASE="https://github.com/AdoptOpenJDK/openjdk${JAVA_VERSION_MAJOR}-binaries/releases/download"

JBANG_HOME="${Q_HOME}/jbang"
JBANG_EXEC="${JBANG_HOME}/bin/jbang"
JBANG_VERSION="0.22.0.2"

PLATFORM="unknown"

# from https://stackoverflow.com/a/8597411/186429
findPlatform() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        PLATFORM="linux"
        JAVA_HOME="${Q_HOME}/jdk"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        PLATFORM="mac"
        JAVA_HOME="${Q_HOME}/jdk/Contents/Home"
    fi
}

installJDK() {
    local file="${JAVA_HOME}/bin/java"
    if [ ! -e "${file}" ]; then
        echo 'Installing Java for Qollider'

        local url="${JAVA_URL_BASE}/jdk-${JAVA_VERSION}%2B${JAVA_VERSION_BUILD}/OpenJDK${JAVA_VERSION_MAJOR}U-jdk_x64_${PLATFORM}_hotspot_${JAVA_VERSION}_${JAVA_VERSION_BUILD}.tar.gz"
        local archive="${Q_HOME}/jdk.tar.gz"

        mkdir -p ${JAVA_HOME}

        curl --location ${url} > ${archive}
        tar -xzvpf ${archive} -C ${JAVA_HOME} --strip-components 1

        rm -drf ${archive}
    fi
}

installJBang() {
    local file="${JBANG_EXEC}"
    if [ ! -e "${file}" ]; then
        local url="https://github.com/jbangdev/jbang/releases/download/v${JBANG_VERSION}/jbang-${JBANG_VERSION}.tar"
        local archive="${Q_HOME}/jbang.tar"

        mkdir -p ${JBANG_HOME}

        curl --location ${url} > ${archive}
        tar -xzvpf ${archive} -C ${JBANG_HOME} --strip-components 1

        rm -drf ${archive}
    fi
}

run() {
    JAVA_HOME=${JAVA_HOME} ${JBANG_EXEC} src/qollider.java "$@"
}

main() {
    mkdir -p ${Q_HOME}
    findPlatform
    installJDK
    installJBang
    run "$@"
}

main "$@"

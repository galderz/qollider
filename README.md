# Qollider - The Quarkus particle accelerator

## Intro

Qollider automates building and running native image tests of Quarkus and related projects.
A typical use of Qollider would first build the JDK,
followed by the GraalVM,
and then would build and test Quarkus.
These steps are optional and can be further expanded to say,
build a particular dependency of Quarkus,
or execute another testsuite.
By limiting its dependencies and keeping them isolated, 
Qollider makes it very easy to share reproducers with others.

## Dependencies

Any dependencies required are installed by the script itself.
In particular, it installs:

* [AdoptOpenJDK](https://adoptopenjdk.net) 14+ - JDK version required to run the main Java script.
* [JBang](https://jbang.dev) - drives the execution of the main Java script.
* Maven - to build and test Maven projects.

### Why not Docker? 

Docker could be used to provide a similar environment with all the dependencies set.
However, running Quarkus native tests on Docker is several times slower.
My server is 8y+ old, so I didn't want to pay that cost.

## Build JDK and GraalVM

To build JDK and GraalVM, execute:

```bash
$ qollider.sh graal-build
```

By default, it builds an
[Oracle Labs JDK 11](https://github.com/graalvm/labs-openjdk-11)
and
[Oracle GraalVM master branch](https://github.com/oracle/graal/tree/master).
Optionally, you can build alternative JDK and GraalVM source trees:

```bash
$ qollider.sh graal-build \
  --jdk-tree https://github.com/openjdk/jdk11u-dev/tree/master \
  --graal-tree https://github.com/graalvm/mandrel/tree/20.1
```

Calling `graal-build` will also download
[mx](https://github.com/graalvm/mx)
tool required to build it.

## Get GraalVM

Sometimes instead of building GraalVM and JDK from scratch,
you want to test a particular GraalVM tar distribution.
You can fetch that distribution by doing:

```bash
$ qollider.sh graal-get \
  --url https://example.com/graalvm.tgz
```

## Build Quarkus or any other Maven project

Qollider can build Quarkus via a generic Maven build target:

```bash
$ qollider.sh maven-build \
  --tree https://github.com/quarkusio/quarkus/tree/master
```

Qollider can build other Maven projects in similar way:

```bash
$ qollider.sh maven-build \
 --tree https://github.com/apache/camel-quarkus/tree/quarkus-master
```

Additional build arguments can be passed in,
for example to skip building a problematic project:

```bash
$ qollider.sh maven-build \
  --tree https://github.com/quarkusio/quarkus/tree/master \
  --additional-build-args -pl,'!:quarkus-integration-test-gradle-plugin'
```

A more complex example would involve building multiple projects,
and making sure they all link with each other.
In this example Qollider first builds a snapshot version of Camel,
then builds Quarkus and finally builds Camel Quarkus with the locally built versions:

```bash
$ qollider.sh maven-build \
 --tree https://github.com/apache/camel/tree/camel-3.2.x \
 --additional-build-args -Pfastinstall

$ qollider.sh maven-build \
 --tree https://github.com/quarkusio/quarkus/tree/master

$ qollider.sh maven-build \
 --tree https://github.com/apache/camel-quarkus/tree/quarkus-master \
 --additional-build-args -Dcamel.version=3.2.1-SNAPSHOT
```

Qollider will ensure that if building projects that depend on Quarkus,
they will be built against the snapshot Quarkus version just built.
This is why there's no mention of the Quarkus version in the example above.

You can find out about new options by calling:

```bash
$ qollider.sh maven-build --help
```

## Test Quarkus

Qollider is mostly interested in executing native image tests,
hence the options it passes internally are strictly related to this kind of testing.

Having called `maven-build` on Quarkus,
testing it is just as simple as calling:

```bash
$ qollider.sh maven-test \
  --suite quarkus
```

Additional test arguments can be passed in (using `,` as separator) for example,
to test only a particular project or continue testing from a given project:

```bash
$ qollider.sh maven-test \
  --suite quarkus \
  --additional-test-args rf,:quarkus-elytron-security-ldap-integration-test
```

Aside from Quarkus, Quarkus Platform contains additional native tests.
These can be executed by first building Quarkus Platform and then testing it:

```bash
$ qollider.sh maven-build \
  --tree https://github.com/quarkusio/quarkus-platform/tree/master
$ qollider.sh maven-test 
  --suite quarkus-platform
```

Further tests can be found in Quarkus Quickstarts.
In this example you can see how to run the native tests in an individual quickstart:

```bash
$ qollider.sh maven-build \
  --tree https://github.com/quarkusio/quarkus-quickstarts/tree/development

./qollider.sh maven-test \
  --suite quarkus-quickstarts \
  --additional-test-args -pl,security-jwt-quickstart
```

If in doubt, you can inspect the help via:

```bash
$ qollider.sh maven-test --help
```

## How it works

When Qollider starts, it creates a `~/.qollider` folder,
where it will install its dependencies.

Then, it creates a folder for the day on which the Qollider is executed,
e.g. `~/.qollider/cache/DDMM`.
This folder contains script specific tools and source directories.
These elements will be built as per the instructions of each Qollider invocation.
This means that if you execute the same script on two different days,
Qollider will download and build them again.
This can be very useful to start from a clean slate.

Qollider uses marker files to signal that a given step within a script has completed.
Assuming the script gets executed again on the same day,
these markers allow steps executed previously to be skipped.
Each marker file contains information of the executed step,
so you can easily inspect them and delete them to re-execute a given step.
Re-executing a given step might also require deleting a particular folder,
if the step involved downloading certain artifact,
or cloning a repository.

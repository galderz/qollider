//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14+
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS org.hamcrest:hamcrest:2.2
//DEPS org.junit.jupiter:junit-jupiter-engine:5.6.1
//DEPS org.junit.platform:junit-platform-launcher:1.6.1

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.INFO;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class qollider
{
    static final System.Logger log = System.getLogger(qollider.class.getName());

    public static void main(String[] args)
    {
        // Run checks
        Check.check();

        final var home = FileSystem.ofHome();
        final var today = FileSystem.ofToday(home);

        final var cli = Cli.read(List.of(args));
        var outputs =
            switch (cli.command)
            {
                case JDK_BUILD -> SystemJdk.build(cli, home, today);
                case GRAAL_GET -> GraalGet.ofSystem(cli, today).call();
                case GRAAL_BUILD -> SystemGraal.build(cli, home, today);
                case MANDREL_BUILD -> SystemMandrel.build(cli, home, today);
                case MAVEN_BUILD -> MavenBuild.ofSystem(cli, home, today).call();
                case MAVEN_TEST -> MavenTest.ofSystem(cli, home, today).call();
            };

        log.log(INFO, "Execution summary:");
        log.log(INFO, "Inputs:{0}{1}"
            , System.lineSeparator()
            , List.of(args).stream()
                .map(Object::toString)
                .collect(Collectors.joining(System.lineSeparator()))
        );
        log.log(INFO, "Outputs:{0}{1}"
            , System.lineSeparator()
            , outputs.stream()
                .map(Object::toString)
                .collect(Collectors.joining(System.lineSeparator()))
        );
    }
}

// Use same command when parameters are same
enum Command
{
    JDK_BUILD
    , GRAAL_GET
    , GRAAL_BUILD
    , MANDREL_BUILD
    , MAVEN_BUILD
    , MAVEN_TEST
}

final class Cli
{
    final Command command;
    private final Map<String, List<String>> params;

    Cli(Command command, Map<String, List<String>> params) {
        this.command = command;
        this.params = params;
    }

    List<String> multi(String name)
    {
        final var options = params.get(name);
        return options == null ? List.of() : options;
    }

    String required(String name)
    {
        final var option = params.get(name);
        if (Objects.isNull(option))
            throw new IllegalArgumentException(String.format(
                "Missing mandatory --%s"
                , name
            ));

        return option.get(0);
    }

    String optional(String name, String defaultValue)
    {
        final var option = params.get(name);
        return option != null ? option.get(0) : defaultValue;
    }

    static Cli read(List<String> args)
    {
        if (args.size() < 1)
            throw new IllegalArgumentException("Not enough arguments");

        final var command =
            Command.valueOf(
                args.get(0).replace("-", "_").toUpperCase()
            );

        final Map<String, List<String>> params = new HashMap<>();

        List<String> options = null;
        for (int i = 1; i < args.size(); i++)
        {
            final var arg = args.get(i);
            if (arg.startsWith("--"))
            {
                if (arg.length() < 3)
                {
                    throw new IllegalArgumentException(
                        String.format("Error at argument %s", arg)
                    );
                }

                options = new ArrayList<>();
                params.put(arg.substring(2), options);
            }
            else if (options != null)
            {
                options.add(arg);
            }
            else
            {
                throw new IllegalArgumentException("Illegal parameter usage");
            }
        }

        return new Cli(command, params);
    }
}

interface Output {}

final class SystemJdk
{
    static List<? extends Output> build(Cli cli, FileSystem home, FileSystem today)
    {
        final var roots = new Roots(home::resolve, today::resolve);

        final var osToday = OperatingSystem.of(today);
        final var osHome = OperatingSystem.of(home);

        final var execToday = Steps.Exec.Effects.of(osToday);
        final var installHome = Steps.Install.Effects.of(Web.of(home), osHome);

        final var get = new Jdk.Get(Jdk.JDK_11_0_7, Path.of("boot-jdk-11"));
        final var getLink = new Steps.Linking.Effects(home::symlink, osHome::type);

        final var build = Jdk.Build.of(cli);
        final var buildLink = Steps.Linking.Effects.of(today::symlink);

        return Lists.concat(
            Jdk.get(get, installHome, getLink)
            , Jdk.build(build, execToday, buildLink, roots)
        );
    }
}

final class Jdk
{
    static final Version JDK_11_0_7 = new Version(11, 0, 7, 10);

    record Version(int major, int minor, int micro, int build)
    {
        String majorMinorMicro()
        {
            return String. format("%d.%d.%d", major, minor, micro);
        }
    }

    enum Type
    {
        OPENJDK
        , LABSJDK
    }

    record Build(Repository tree)
    {
        Type javaType()
        {
            return type(tree);
        }

        static Build of(Cli cli)
        {
            final var tree = Repository.of(cli.optional(
                "tree"
                , "https://github.com/openjdk/jdk11u-dev"
            ));

            return new Build(tree);
        }

        private static Jdk.Type type(Repository repo)
        {
            return repo.name().startsWith("labs") ? Type.LABSJDK : Type.OPENJDK;
        }
    }

    static List<? extends Output> get(
        Get get
        , Steps.Install.Effects install
        , Steps.Linking.Effects linking
    )
    {
        final var getOut = get(get, install);
        final var linkOut = link(get, linking);
        return Lists.append(linkOut, getOut);
    }

    private static Link link(Get get, Steps.Linking.Effects effects)
    {
        final var link = Homes.bootJdk();
        final var target =
            effects.os().get().isMac()
                ? get.path.resolve(Path.of("Contents", "Home"))
                : get.path;

        final var linking = new Steps.Linking(link, target);
        return Steps.Linking.link(linking, effects);
    }

    static List<? extends Output> build(
        Build build
        , Steps.Exec.Effects exec
        , Steps.Linking.Effects linking
        , Roots roots
    )
    {
        // TODO Remove List.of when rest of calls have migrated
        final var cloneOut = List.of(
            clone(build.tree(), exec)
        );

        final var buildSteps = switch (build.javaType())
        {
            case OPENJDK -> OpenJDK.buildSteps(build, roots);
            case LABSJDK -> LabsJDK.buildSteps(build, roots);
        };

        final var buildOut = buildSteps
            .map(t -> Steps.Exec.run(t, exec))
            .collect(Collectors.toList());

        // TODO Remove List.of when rest of calls have migrated
        final var linkOut = List.of(
            link(build, linking)
        );

        // TODO Use typed flatten
        return Lists.flatten(cloneOut, buildOut, linkOut);
    }

    static Marker clone(Repository tree, Steps.Exec.Effects effects)
    {
        return switch (tree.type())
        {
            case GIT -> Git.clone(tree, effects);
            case MERCURIAL -> Mercurial.clone(tree, effects);
        };
    }

    private static Link link(Build build, Steps.Linking.Effects effects)
    {
        final var jdkPath = Path.of(build.tree.name());
        final var target = switch (build.javaType())
        {
            case OPENJDK -> OpenJDK.javaHome(jdkPath);
            case LABSJDK -> LabsJDK.javaHome(jdkPath);
        };

        final var link = Homes.java();
        final var linking = new Steps.Linking(link, target);
        return Steps.Linking.link(linking, effects);
    }

    private static final class OpenJDK
    {
        static Stream<Steps.Exec> buildSteps(Build build, Roots roots)
        {
            return Stream.of(configure(build, roots), make(build));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve(
                Path.of(
                    "build"
                    , "graal-server-release"
                    , "images"
                    , "graal-builder-jdk"
                )
            );
        }

        private static Steps.Exec configure(Build build, Roots roots)
        {
            return Steps.Exec.of(
                Path.of(build.tree.name())
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                // Workaround for https://bugs.openjdk.java.net/browse/JDK-8235903 on newer GCC versions
                , "--with-extra-cflags=-fcommon"
                , "--enable-aot=no"
                , format("--with-boot-jdk=%s", roots.home().apply(Homes.bootJdk()))
            );
        }

        private static Steps.Exec make(Build build)
        {
            return Steps.Exec.of(
                Path.of(build.tree.name())
                , "make"
                , "graal-builder-image"
            );
        }
    }

    private static final class LabsJDK
    {
        static Stream<Steps.Exec> buildSteps(Build build, Roots roots)
        {
            return Stream.of(buildJDK(build, roots));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve("java_home");
        }

        private static Steps.Exec buildJDK(Build build, Roots roots)
        {
            return Steps.Exec.of(
                Path.of(build.tree.name())
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , roots.home().apply(Homes.bootJdk()).toString()
            );
        }
    }

    record Get(Jdk.Version version, Path path) {}

    private static List<? extends Output> get(Get get, Steps.Install.Effects install)
    {
        final var javaBaseUrl = format(
            "https://github.com/AdoptOpenJDK/openjdk%d-binaries/releases/download"
            , get.version().major()
        );

        final var osType = install.download().osType().get();
        final var javaOsType = osType.isMac() ? "mac" : osType.toString();
        final var arch = "x64";

        final var url = URLs.of(
            "%s/jdk-%s%%2B%d/OpenJDK%sU-jdk_%s_%s_hotspot_%s_%s.tar.gz"
            , javaBaseUrl
            , get.version().majorMinorMicro()
            , get.version().build()
            , get.version().major()
            , arch
            , javaOsType
            , get.version().majorMinorMicro()
            , get.version().build()
        );

        return Steps.Install.install(new Steps.Install(url, get.path), install);
    }
}

// TODO Move to Graal
@Deprecated
class GraalGet implements Callable<List<?>>
{
    final Options options;
    final FileSystem fs;

    GraalGet(Options options, FileSystem fs) {
        this.options = options;
        this.fs = fs;
    }

    static GraalGet ofSystem(Cli cli, FileSystem fs)
    {
        return new GraalGet(Options.of(cli), fs);
    }

    @Override
    public List<?> call()
    {
        final var os = OperatingSystem.of(fs);
        final var web = Web.of(fs);

        final var exec = Steps.Exec.Effects.of(os);
        final var install = Steps.Install.Effects.of(web, os);

        return Lists.flatten(
            Graal.get(options, exec, install)
            , Graal.link(options, fs::symlink)
        );
    }

    static final class Graal
    {
        static List<Marker> get(
            Options options
            , Steps.Exec.Effects exec
            , Steps.Install.Effects install
        )
        {
            final var installMarkers = Steps.Install.install(
                new Steps.Install(options.url, options.graal)
                , install
            );

            final var orgName = Path.of(options.url.getPath()).getName(0);
            if (!orgName.equals(Path.of("graalvm")))
                return installMarkers;

            final var nativeImageMarker = Steps.Exec.run(
                Steps.Exec.of(
                    Path.of("graal", "bin")
                    , "./gu"
                    , "install"
                    , "native-image"
                )
                , exec
            );

            return Lists.append(nativeImageMarker, installMarkers);
        }

        public static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var link = Homes.graal();
            return symLink.apply(link, options.graal);
        }
    }

    record Options(URL url, Path graal)
    {
        static Options of(Cli cli)
        {
            return new Options(
                URLs.of(cli.required("url"))
                , Path.of("graalvm", "graal")
            );
        }
    }
}

final class SystemGraal
{
    static List<? extends Output> build(Cli cli, FileSystem home, FileSystem today)
    {
        final var osToday = OperatingSystem.of(today);
        final var roots = new Roots(home::resolve, today::resolve);
        final var execToday = Steps.Exec.Effects.of(osToday);
        final var linking = Steps.Linking.Effects.of(today::symlink);
        return Graal.build(Graal.Build.of(cli), execToday, linking, roots);
    }
}

final class Graal
{
    record Build(
        Repository tree
        , Repository mx
    )
    {
        static Build of(Cli cli)
        {
            var tree = Repository.of(
                cli.optional(
                    "tree"
                    , "https://github.com/oracle/graal/tree/master"
                )
            );
            var mx = Repository.of(
                cli.optional(
                    "mx-tree"
                    , "https://github.com/graalvm/mx/tree/master"
                )
            );

            return new Build(tree, mx);
        }
    }

    static List<? extends Output> build(
        Build build
        , Steps.Exec.Effects exec
        , Steps.Linking.Effects linking
        , Roots roots
    )
    {
        var mxOut = Git.clone(build.mx, exec);

        var treeOut = Git.clone(build.tree, exec);

        final var svm = Path.of(build.tree.name(), "substratevm");
        var buildOut = Steps.Exec.run(
            Steps.Exec.of(
                svm
                , roots.today().apply(Path.of(build.mx.name(), "mx")).toString()
                , "--java-home"
                , roots.today().apply(Homes.java()).toString()
                , "build"
            )
            , exec
        );

        final var target = Path.of(
            build.tree.name()
            ,"sdk"
            , "latest_graalvm_home"
        );

        final var linkOut = Steps.Linking.link(
            new Steps.Linking(Homes.graal(), target)
            , linking
        );

        return Lists.flatten(mxOut, treeOut, buildOut, linkOut);
    }
}

final class SystemMandrel
{
    static List<? extends Output> build(Cli cli, FileSystem home, FileSystem today)
    {
        final var osToday = OperatingSystem.of(today);
        final var osHome = OperatingSystem.of(home);
        final var roots = new Roots(home::resolve, today::resolve);
        final var execToday = Steps.Exec.Effects.of(osToday);
        final var installHome = Steps.Install.Effects.of(Web.of(home), osHome);
        return Mandrel.build(Mandrel.Build.of(cli), execToday, installHome, roots);
    }
}

final class Mandrel
{
    record Build(
        Repository tree
        , Repository mx
        , Repository packaging
    )
    {
        static Build of(Cli cli)
        {
            var tree = Repository.of(
                cli.optional(
                    "tree"
                    , "https://github.com/graalvm/mandrel/tree/mandrel/20.1"
                )
            );
            var mx = Repository.of(
                cli.optional(
                    "mx-tree"
                    , "https://github.com/graalvm/mx/tree/master"
                )
            );
            var packaging = Repository.of(
                cli.optional(
                    "packaging-tree"
                    , "https://github.com/graalvm/mandrel-packaging/tree/master"
                )
            );
            return new Build(tree, mx, packaging);
        }
    }

    static List<? extends Output> build(
        Build build
        , Steps.Exec.Effects exec
        , Steps.Install.Effects install
        , Roots roots
    )
    {
        final var treeOut = Git.clone(build.tree, exec);
        final var mxOut = Git.clone(build.mx, exec);
        final var packagingOut = Git.clone(build.packaging, exec);

        final var mavenOut = Maven.install(install);

        final var today = roots.today();
        final var buildOut = Steps.Exec.run(
            Steps.Exec.of(
                Path.of("mandrel-packaging")
                , List.of(
                    EnvVar.javaHome(today.apply(Homes.java()))
                    , EnvVar.of("MX_HOME", today.apply(Path.of(build.mx.name())))
                    , EnvVar.of("MANDREL_REPO", today.apply(Path.of(build.tree.name())))
                    , EnvVar.of("MANDREL_HOME", today.apply(Homes.graal()))
                    , EnvVar.of("MAVEN_HOME", Maven.home(roots.home()))
                )
                , "./buildJDK.sh"
            )
            , exec
        );

        // TODO Create Lists.flatten(List<E>...)
        final var result = Lists.mutable(treeOut, mxOut, packagingOut);
        result.addAll(mavenOut);
        result.add(buildOut);
        return result;
    }
}

class MavenBuild implements Callable<List<?>>
{
    // TODO if -Dnative, add additional args for -J-ea and -J-esa

    // TODO avoid duplication with MavenTest
    // TODO read camel-quarkus snapshot version from pom.xml
    // TODO make it not stating (can't use Stream because of unit tests), convert into defaults record instead
    private static final Map<String, List<String>> EXTRA_BUILD_ARGS = Map.of(
        "camel-quarkus"
        , List.of("-Dquarkus.version=999-SNAPSHOT")
        , "quarkus-platform"
        , List.of(
            "-Dquarkus.version=999-SNAPSHOT"
            , "-Dcamel-quarkus.version=1.1.0-SNAPSHOT"
        )
    );

    final Options options;
    final FileSystem home;
    final FileSystem today;

    MavenBuild(Options options, FileSystem home, FileSystem today) {
        this.options = options;
        this.home = home;
        this.today = today;
    }

    static MavenBuild ofSystem(Cli cli, FileSystem home, FileSystem today)
    {
        return new MavenBuild(Options.of(cli), home, today);
    }

    @Override
    public List<?> call()
    {
        final var osToday = OperatingSystem.of(today);
        final var osHome = OperatingSystem.of(home);
        final var roots = new Roots(home::resolve, today::resolve);

        final var execToday = Steps.Exec.Effects.of(osToday);
        final var installHome = Steps.Install.Effects.of(Web.of(home), osHome);

        return Lists.flatten(
            Git.clone(options.tree, execToday)
            , Maven.install(installHome)
            , MavenBuild.build(options, execToday, roots)
        );
    }

    record Options(Repository tree, List<String> additionalBuildArgs)
    {
        Path project()
        {
            return Path.of(tree.name());
        }

        static Options of(Cli cli)
        {
            return new Options(
                Repository.of(cli.required("tree"))
                , cli.multi("additional-build-args")
            );
        }
    }

    static Marker build(Options options, Steps.Exec.Effects effects, Roots roots)
    {
        final var project = options.project();
        final var arguments =
            arguments(options, project.toString(), roots)
                .toArray(String[]::new);

        // Use a java home that already points to the jdk + graal.
        // Enables maven build to be used to build for sample Quarkus apps with native bits.
        // This is not strictly necessary for say building Quarkus.
        final var envVars = List.of(EnvVar.javaHome(roots.today().apply(Homes.graal())));
        return Steps.Exec.run(
            Steps.Exec.of(project, envVars, arguments)
            , effects
        );
    }

    private static Stream<String> arguments(Options options, String directory, Roots roots)
    {
        // TODO would adding -Dmaven.test.skip=true work? it skips compiling tests...
        final var arguments = Stream.concat(
            Stream.of(
                Maven.mvn(roots.home()).toString()
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
            )
            , options.additionalBuildArgs.stream()
        );

        final var extra = EXTRA_BUILD_ARGS.get(directory);
        return Objects.isNull(extra)
            ? arguments
            : Stream.concat(arguments, extra.stream());
    }
}

class MavenTest implements Callable<List<?>>
{
    // TODO if -Dnative, add additional args for -J-ea and -J-esa

    // TODO avoid duplication with MavenBuild
    // TODO read camel-quarkus snapshot version from
    // TODO make it not stating (can't use Stream because of unit tests), convert into defaults record instead
    private static final Map<Path, List<String>> EXTRA_TEST_ARGS = Map.of(
        Path.of("quarkus-platform")
        , List.of(
            "-Dquarkus.version=999-SNAPSHOT"
            , "-Dcamel-quarkus.version=1.1.0-SNAPSHOT"
        )
    );

    final Options options;
    final FileSystem home;
    final FileSystem today;

    MavenTest(Options options, FileSystem home, FileSystem today) {
        this.options = options;
        this.home = home;
        this.today = today;
    }

    static MavenTest ofSystem(Cli cli, FileSystem home, FileSystem today)
    {
        return new MavenTest(Options.of(cli), home, today);
    }

    @Override
    public List<?> call()
    {
        final var os = OperatingSystem.of(today);
        MavenTest.test(options, os::exec, new Roots(home::resolve, today::resolve));
        // TODO make it return markers so that it can be tested just like other commands
        return List.of();
    }

    record Options(String suite, List<String> additionalTestArgs)
    {
        static Options of(Cli cli)
        {
            return new Options(
                cli.required("suite")
                , cli.multi("additional-test-args")
            );
        }
    }

    static void test(Options options, Consumer<Steps.Exec> exec, Roots roots)
    {
        final var task = MavenTest.toTest(options, roots);
        MavenTest.doTest(task, exec);
    }

    private static void doTest(
        Steps.Exec task
        , Consumer<Steps.Exec> exec
    )
    {
        exec.accept(task);
    }

    private static Steps.Exec toTest(Options options, Roots roots)
    {
        final var directory = MavenTest.suitePath(options.suite);
        final var arguments = arguments(options, directory, roots).toArray(String[]::new);
        final var envVars = List.of(EnvVar.javaHome(roots.today().apply(Homes.graal())));
        return Steps.Exec.of(directory, envVars, arguments);
    }

    private static Stream<String> arguments(Options options, Path directory, Roots roots)
    {
        final var args = Stream.of(
            Maven.mvn(roots.home()).toString()
            , "install"
            , "-Dnative"
            , "-Dformat.skip"
        );

        final var userArgs = options.additionalTestArgs;
        final var extraArgs = EXTRA_TEST_ARGS.get(directory);
        if (Objects.nonNull(userArgs) && Objects.nonNull(extraArgs))
        {
            return Stream
                .of(args, extraArgs.stream(), userArgs.stream())
                .flatMap(Function.identity());
        }

        if (Objects.nonNull(extraArgs))
        {
            return Stream.concat(args, extraArgs.stream());
        }

        if (Objects.nonNull(userArgs))
        {
            return Stream.concat(args, userArgs.stream());
        }

        return args;
    }

    private static Path suitePath(String suite)
    {
        return suite.equals("quarkus")
            ? Path.of("quarkus", "integration-tests")
            : Path.of(suite);
    }
}

final class Maven
{
    static List<Marker> install(Steps.Install.Effects install)
    {
        final var version = "3.6.3";
        final var url = URLs.of(
            "https://downloads.apache.org/maven/maven-3/%1$s/binaries/apache-maven-%1$s-bin.tar.gz"
            , version
        );

        return Steps.Install.install(new Steps.Install(url, Path.of("maven")), install);
    }

    static Path home(Function<Path, Path> resolve)
    {
        return resolve.apply(Path.of("maven"));
    }

    static Path mvn(Function<Path, Path> resolve)
    {
        return home(resolve).resolve(Path.of("bin", "mvn"));
    }
}

// TODO depth might not be needed once --force is implemented (forces git clone + rebuild)
record Repository(
    String organization
    , String name
    , Repository.Type type
    , String branch
    , int depth
    , URI cloneUri
)
{
    static Repository of(String uri)
    {
        return Repository.of(URI.create(uri));
    }

    static Repository of(URI uri)
    {
        final var host = uri.getHost();
        final var path = Path.of(uri.getPath());
        if (host.equals("github.com"))
        {
            final var organization = path.getName(0).toString();
            final var name = path.getName(1).toString();
            final var branch = extractBranch(path).toString();
            final var depth = extractDepth(uri, name);
            final var cloneUri = gitCloneUri(organization, name);
            return new Repository(organization, name, Type.GIT, branch, depth, cloneUri);
        }
        else if (host.equals("hg.openjdk.java.net"))
        {
            final var organization = "openjdk";
            final var name = path.getFileName().toString();
            return new Repository(organization, name, Type.MERCURIAL, null, 1, uri);
        }
        throw Illegal.value(host);
    }

    private static int extractDepth(URI uri, String repoName)
    {
        final var params = URIs.splitQuery(uri);
        final var value = params.get("depth");
        if (Objects.nonNull(value))
        {
            return Integer.parseInt(value);
        }

        return repoName.equals("labs-openjdk-11")
            ? 20
            : 1;
    }

    private static URI gitCloneUri(String organization, String name)
    {
        try
        {
            final var path = Path.of(
                "/"
                , organization
                , name
            );

            return new URI(
                "https"
                , "github.com"
                , path.toString()
                , null
            );
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Path extractBranch(Path path)
    {
        final int base = 3;
        if (path.getNameCount() == (base + 1))
            return path.getFileName();

        final var numPathElements = path.getNameCount() - base;
        final var pathElements = new String[numPathElements];
        int index = 0;
        while (index < numPathElements)
        {
            pathElements[index] = path.getName(base + index).toString();
            index++;
        }

        final var first = pathElements[0];
        final var numMoreElements = numPathElements - 1;
        final var more = new String[numMoreElements];
        System.arraycopy(pathElements, 1, more, 0, numMoreElements);
        return Path.of(first, more);
    }

    enum Type
    {
        GIT
        , MERCURIAL
    }
}

class Mercurial
{
    static Marker clone(Repository repository, Steps.Exec.Effects effects)
    {
        return Steps.Exec.run(
            Steps.Exec.of(
                "hg"
                , "clone"
                , repository.cloneUri().toString()
            )
            , effects
        );
    }
}

record EnvVar(String name, Path path)
{
    static EnvVar of(String name, String path)
    {
        return of(name, Path.of(path));
    }

    static EnvVar of(String name, Path path)
    {
        return new EnvVar(name, path);
    }

    static EnvVar javaHome(Path path)
    {
        return of("JAVA_HOME", path);
    }
}

record Roots(Function<Path, Path> home, Function<Path, Path> today) {}

interface Step {}

// TODO remove steps and just use Step (eventually sealed types in Java 15)
final class Steps
{
    // Install = Download + Extract
    record Install(URL url, Path path) implements Step
    {
        static List<Marker> install(Install install, Install.Effects effects)
        {
            final var url = install.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var tarPath = directory.resolve(fileName);

            final var downloadMarker = Download.lazy(
                new Download(url, tarPath)
                , effects.download
            );

            final var extractMarker = Extract.extract(
                new Extract(tarPath, install.path)
                , effects.extract
            );

            return List.of(downloadMarker, extractMarker);
        }

        record Effects(Download.Effects download, Extract.Effects extract)
        {
            static Effects of(Web web, OperatingSystem os)
            {
                final var download = Download.Effects.of(web, os);
                final var extract = Extract.Effects.of(os);
                return new Effects(download, extract);
            }
        }
    }

    record Extract(Path tar, Path path) implements Step
    {
        static Marker extract(Extract extract, Extract.Effects effects)
        {
            effects.mkdirs.accept(extract.path); // cheap so do it regardless, no marker

            return Exec.run(
                Exec.of(
                    "tar"
                    // TODO make it quiet
                    , "-xzvpf"
                    , extract.tar.toString()
                    , "-C"
                    , extract.path.toString()
                    , "--strip-components"
                    , "1"
                )
                , effects.exec
            );
        }

        record Effects(
            Exec.Effects exec
            , Consumer<Path> mkdirs
        )
        {
            static Effects of(OperatingSystem os)
            {
                final var exec = Exec.Effects.of(os);
                return new Effects(exec, os.fs::mkdirs);
            }
        }
    }

    // TODO create better to string: two line, first with folder, second with command, see command line for ideas
    record Exec(
        List<String> args
        , Path directory
        , List<EnvVar> envVars
    ) implements Step
    {
        static Exec of(Path path, List<EnvVar> envVars, String... args)
        {
            return new Exec(List.of(args), path, envVars);
        }

        static Exec of(Path path, String... args)
        {
            return new Exec(List.of(args), path, List.of());
        }

        static Exec of(String... args)
        {
            return new Exec(Arrays.asList(args), Path.of(""), emptyList());
        }

        static Marker run(Exec exec, Effects effects)
        {
            final var marker = Marker.of(exec).query(effects.exists);
            if (marker.exists())
                return marker;

            effects.exec().accept(exec);
            return marker.touch(effects.touch);
        }

        record Effects(
            Predicate<Path> exists
            , Consumer<Exec> exec
            , Function<Marker, Boolean> touch
        )
        {
            static Effects of(OperatingSystem os)
            {
                return new Effects(os.fs::exists, os::exec, os.fs::touch);
            }
        }
    }

    record Download(URL url, Path path) implements Step
    {
        static Marker lazy(Download task, Effects effects)
        {
            final var marker = Marker.of(task).query(effects.exists);
            if (marker.exists())
                return marker;

            effects.download.accept(task);
            return marker.touch(effects.touch);
        }

        record Effects(
            Predicate<Path> exists
            , Function<Marker, Boolean> touch
            , Consumer<Download> download
            , Supplier<OperatingSystem.Type> osType
        )
        {
            static Effects of(Web web, OperatingSystem os)
            {
                return new Effects(os.fs::exists, os.fs::touch, web::download, os::type);
            }
        }
    }

    record Linking(Path link, Path target) implements Step
    {
        static Link link(Linking linking, Effects effects)
        {
            final var target = linking.target;
            return effects.symLink.apply(linking.link, target);
        }

        record Effects(BiFunction<Path, Path, Link> symLink, Supplier<OperatingSystem.Type> os)
        {
            static Effects of(BiFunction<Path, Path, Link> symLink)
            {
                return new Effects(symLink, () -> OperatingSystem.Type.UNKNOWN);
            }
        }
    }
}

// TODO if the marker is gone (e.g. it doesn't exist) remove any existing folder and clone again (saves a step on user)
class Git
{
    static Marker clone(Repository repo, Steps.Exec.Effects effects)
    {
        return Steps.Exec.run(
            Steps.Exec.of(toClone(repo))
            , effects
        );
    }

    static String[] toClone(Repository repo)
    {
        final var result = Lists.mutable(
            "git"
            , "clone"
            , "-b"
            , repo.branch()
        );

        if (repo.depth() > 0)
        {
            result.add("--depth");
            result.add(String.valueOf(repo.depth()));
        }

        result.add(repo.cloneUri().toString());

        return result.toArray(String[]::new);
    }
}

// TODO still needed? Merge with roots or Jdk/Graal?
final class Homes
{
    static Path java()
    {
        return Path.of("java_home");
    }

    static Path bootJdk()
    {
        return Path.of("bootjdk_home");
    }

    static Path graal()
    {
        return Path.of("graalvm_home");
    }
}

// Boundary value
record Marker(boolean exists, boolean touched, Path path, String cause) implements Output
{
    Marker query(Predicate<Path> existsFn)
    {
        final var exists = existsFn.test(this.path);
        if (exists)
        {
            return new Marker(true, touched, path, cause);
        }

        return new Marker(false, touched, path, cause);
    }

    Marker touch(Function<Marker, Boolean> touchFn)
    {
        if (exists)
        {
            return this;
        }

        final var touched = touchFn.apply(this);
        if (touched)
        {
            return new Marker(true, true, path, cause);
        }

        return new Marker(false, false, path, cause);
    }

    static Marker of(Step step)
    {
        final var producer = step.toString();
        final var hash = Hashing.sha1(producer);
        final var path = Path.of(format("%s.marker", hash));
        return new Marker(false, false, path, producer);
    }
}

// Boundary value
record Link(Path link, Path target) implements Output {}

// Dependency
class FileSystem
{
    static final System.Logger LOG = System.getLogger(qollider.class.getName());

    final Path root;

    static FileSystem ofToday(FileSystem home)
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("ddMM");
        var today = date.format(formatter);
        var baseDir = home.root.resolve("cache");
        final var path = baseDir.resolve(today);
        final var isNewDay = !path.toFile().exists();
        var fs = FileSystem.of(path);
        if (isNewDay)
        {
            home.symlink(Path.of("cache", "latest"), Path.of(today));
        }
        return fs;
    }

    static FileSystem ofHome()
    {
        var home = Path.of(System.getProperty("user.home"), ".qollider");
        return FileSystem.of(home);
    }

    private static FileSystem of(Path path)
    {
        return new FileSystem(idempotentMkDirs(path));
    }

    private static Path idempotentMkDirs(Path directory)
    {
        final var directoryFile = directory.toFile();
        if (!directoryFile.exists() && !directoryFile.mkdirs())
        {
            throw new RuntimeException(format(
                "Unable to create directory: %s"
                , directory)
            );
        }

        return directory;
    }

    void mkdirs(Path directory)
    {
        FileSystem.idempotentMkDirs(root.resolve(directory));
    }

    boolean exists(Path path)
    {
        return root.resolve(path).toFile().exists();
    }

    boolean touch(Marker marker)
    {
        final var path = root.resolve(marker.path());
        try
        {
            Files.writeString(path, marker.cause());
            return true;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    Link symlink(Path relativeLink, Path relativeTarget)
    {
        final var link = root.resolve(relativeLink);
        try
        {
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS))
            {
                Files.delete(link);
            }

            final var symbolicLink = Files.createSymbolicLink(link, relativeTarget);
            return new Link(symbolicLink, relativeTarget);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    Path resolve(Path other)
    {
        return root.resolve(other);
    }

    // TODO use again when reimplementing clean
    void deleteRecursive(Path relative)
    {
        try
        {
            final var path = root.resolve(relative);

            final var notDeleted =
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(Predicate.not(File::delete))
                    .collect(Collectors.toList());

            if (!notDeleted.isEmpty())
            {
                throw new RuntimeException(format(
                    "Unable to delete %s files"
                    , notDeleted
                ));
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private FileSystem(Path root)
    {
        this.root = root;
    }
}

// Dependency
class OperatingSystem
{
    static final System.Logger LOG = System.getLogger(qollider.class.getName());

    final FileSystem fs;

    private OperatingSystem(FileSystem fs)
    {
        this.fs = fs;
    }

    static OperatingSystem of(FileSystem fs)
    {
        return new OperatingSystem(fs);
    }

    void exec(Steps.Exec exec)
    {
        final var taskList = exec.args().stream()
            .filter(Predicate.not(String::isEmpty))
            .collect(Collectors.toList());

        final var directory = fs.root.resolve(exec.directory());
        // TODO print task without commas
        LOG.log(INFO
            ,"Execute {0} in {1} with environment variables {2}"
            , taskList
            , directory
            , exec.envVars()
        );
        try
        {
            var processBuilder = new ProcessBuilder(taskList)
                .directory(directory.toFile())
                .inheritIO();

            exec.envVars().forEach(
                envVar ->
                    processBuilder.environment().put(
                        envVar.name()
                        , envVar.path().toString()
                    )
            );

            Process process = processBuilder.start();

            if (process.waitFor() != 0)
            {
                throw new RuntimeException(
                    "Failed, exit code: " + process.exitValue()
                );
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    enum Type
    {
        WINDOWS
        , MAC_OS
        , LINUX
        , UNKNOWN
        ;

        boolean isMac()
        {
            return this == MAC_OS;
        }

        @Override
        public String toString()
        {
            return super.toString().toLowerCase();
        }
    }

    Type type()
    {
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);

        if ((OS.contains("mac")) || (OS.contains("darwin")))
            return Type.MAC_OS;

        if (OS.contains("win"))
            return Type.WINDOWS;

        if (OS.contains("nux"))
            return Type.LINUX;

        return Type.UNKNOWN;
    }
}

final class Hashing
{
    public static final MessageDigest SHA1 = getSha1();

    static String sha1(String s)
    {
        SHA1.update(s.getBytes(StandardCharsets.UTF_8));
        return format("%x", new BigInteger(1, SHA1.digest()));
    }

    private static MessageDigest getSha1()
    {
        try
        {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}

// Dependency
final class Web
{
    static final System.Logger LOG = System.getLogger(qollider.class.getName());

    final FileSystem fs;

    private Web(FileSystem fs)
    {
        this.fs = fs;
    }

    static Web of(FileSystem fs)
    {
        return new Web(fs);
    }

    void download(Steps.Download download)
    {
        try
        {
            final var urlChannel = new DownloadProgressChannel(
                Channels.newChannel(download.url().openStream())
            );

            // Create any parent directories as needed
            fs.mkdirs(download.path().getParent());

            final var path = fs.root.resolve(download.path());
            final var os = new FileOutputStream(path.toFile());
            final var fileChannel = os.getChannel();

            fileChannel.transferFrom(urlChannel, 0, Long.MAX_VALUE);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static final class DownloadProgressChannel implements ReadableByteChannel
    {
        private static final long LOG_CHUNK_SIZE = 25 * 1024 * 1024;

        final ReadableByteChannel channel;

        long bytesCount;
        int logCount;

        private DownloadProgressChannel(ReadableByteChannel channel)
        {
            this.channel = channel;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
            int bytesRead;

            bytesRead = channel.read(dst);
            if (bytesRead > 0)
            {
                bytesCount += bytesRead;
                final var logChunk = (int) (bytesCount / LOG_CHUNK_SIZE);
                if (logChunk != logCount)
                {
                    logCount = logChunk;
                    LOG.log(INFO
                        ,"Download progress {0} received"
                        , humanReadableByteCountBin(bytesCount)
                    );
                }
            }
            return bytesRead;
        }

        // https://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
        public static String humanReadableByteCountBin(long bytes)
        {
            long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            if (absB < 1024)
            {
                return bytes + " B";
            }
            long value = absB;
            CharacterIterator ci = new StringCharacterIterator("KMGTPE");
            for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10)
            {
                value >>= 10;
                ci.next();
            }
            value *= Long.signum(bytes);
            return format("%.1f %ciB", value / 1024.0, ci.current());
        }

        @Override
        public boolean isOpen()
        {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException
        {
            channel.close();
        }
    }
}

final class URLs
{
    static URL of(String url)
    {
        try
        {
            return new URL(url);
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    static URL of(String format, Object... args)
    {
        return URLs.of(format(format, args));
    }
}

final class URIs
{
    public static Map<String, String> splitQuery(URI uri)
    {
        if (uri.getRawQuery() == null || uri.getRawQuery().isEmpty())
        {
            return Collections.emptyMap();
        }

        return Stream.of(uri.getRawQuery().split("&"))
            .map(e -> e.split("="))
            .collect(
                Collectors.toMap(
                    e -> URLDecoder.decode(e[0], StandardCharsets.UTF_8)
                    , e -> URLDecoder.decode(e[1], StandardCharsets.UTF_8)
                )
            );
    }
}

final class Lists
{
    static <E> List<E> append(E element, List<? extends E> list)
    {
        final var result = new ArrayList<E>(list);
        result.add(element);
        return Collections.unmodifiableList(result);
    }

    static <E> List<E> prepend(E element, List<E> list)
    {
        final var result = new ArrayList<E>(list.size() + 1);
        result.add(element);
        result.addAll(list);
        return Collections.unmodifiableList(result);
    }

    static <E> List<E> concat(List<? extends E> l1, List<? extends E> l2)
    {
        return Stream.of(l1, l2)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    // TODO make it typesafe
    static <E> List<E> flatten(Object... elements)
    {
        final var result = new ArrayList<E>();
        for (Object element : elements)
        {
            if (element instanceof List<?> l)
            {
                result.addAll(Unchecked.cast(l));
            }
            else
            {
                result.add(Unchecked.cast(element));
            }
        }
        return result;
    }

    @SafeVarargs
    static <E> List<E> mutable(E... a)
    {
        return new ArrayList<>(List.of(a));
    }
}

final class Unchecked
{
    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj)
    {
        return (T) obj;
    }
}

final class Illegal
{
    static IllegalStateException value(String value)
    {
        return new IllegalStateException(format("Unexpected value: %s", value));
    }
}

final class Check
{
    static final System.Logger LOG = System.getLogger(qollider.class.getName());

    static void check()
    {
        // TODO remove summary listener, a bit too noisy
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectClass(CheckGit.class)
                , selectClass(CheckJdk.class)
                , selectClass(CheckGraal.class)
                , selectClass(CheckMandrel.class)
                , selectClass(CheckGraalGet.class)
                , selectClass(CheckMavenBuild.class)
                , selectClass(CheckMavenTest.class)
            )
            .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        final var summary = listener.getSummary();
        final var failureCount = summary.getTestsFailedCount();
        if (summary.getTestsFailedCount() == 0)
        {
            final var duration = summary.getTimeFinished() - summary.getTimeStarted();
            LOG.log(INFO
                , "Tests ({0}) run successfully after {1} ms"
                , summary.getTestsSucceededCount()
                , duration
            );
        }
        else
        {
            summary.printFailuresTo(new PrintWriter(System.err));
            throw new AssertionError(format(
                "Number of failed tests: %d"
                , failureCount
            ));
        }
    }

    static class CheckGit
    {
        @Test
        void uri()
        {
            final var repo = Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master");
            assertThat(repo.organization(), is("openjdk"));
            assertThat(repo.name(), is("jdk11u-dev"));
            assertThat(repo.branch(), is("master"));
            assertThat(repo.depth(), is(1));
            assertThat(repo.cloneUri(), is(URI.create("https://github.com/openjdk/jdk11u-dev")));
        }

        @Test
        void uriWithDepth()
        {
            final var repo = Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master?depth=0");
            assertThat(repo.organization(), is("openjdk"));
            assertThat(repo.name(), is("jdk11u-dev"));
            assertThat(repo.branch(), is("master"));
            assertThat(repo.depth(), is(0));
            assertThat(repo.cloneUri(), is(URI.create("https://github.com/openjdk/jdk11u-dev")));
        }

        @Test
        void gitCloneSkip()
        {
            final var args = new String[]{
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "1"
                , "https://github.com/openjdk/jdk11u-dev"
            };
            final var fs = InMemoryFileSystem.ofExists(Steps.Exec.of(args));
            final var os = RecordingOperatingSystem.macOS();
            final Repository repo = Repository.of(
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var marker = Git.clone(repo, effects);
            os.assertNoTask(marker);
        }

        @Test
        void gitCloneSingle()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final Repository repo = Repository.of(
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var marker = Git.clone(repo, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "1"
                , "https://github.com/openjdk/jdk11u-dev"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void gitCloneLabsJDK()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final Repository repo = Repository.of(
                "https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var marker = Git.clone(repo, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "jvmci-20.2-b02"
                , "--depth"
                , "20"
                , "https://github.com/graalvm/labs-openjdk-11"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void gitCloneFull()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final Repository repo = Repository.of(
                "https://github.com/openjdk/jdk11u-dev/tree/master?depth=0"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var marker = Git.clone(repo, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "master"
                , "https://github.com/openjdk/jdk11u-dev"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void branchWithPath()
        {
            final var url = Repository.of("https://github.com/olpaw/graal/tree/paw/2367");
            assertThat(url.organization(), is("olpaw"));
            assertThat(url.name(), is("graal"));
            assertThat(url.branch(), is("paw/2367"));
            assertThat(url.cloneUri(), is(URI.create("https://github.com/olpaw/graal")));
        }
    }

    static class CheckJdk
    {
        static List<? extends Output> jdkBuild(InMemoryFileSystem fs, String... extra)
        {
            final var cli = Cli.read(Lists.prepend("jdk-build", List.of(extra)));
            return Jdk.build(
                Jdk.Build.of(cli)
                , fs.exec()
                , fs.linking()
                , Args.roots()
            );
        }

        @Test
        void buildLabsJdk()
        {
            Asserts.steps2(
                jdkBuild(InMemoryFileSystem.empty(), Args.labsJdkTree())
                , Expect.gitLabsJdkClone()
                , Expect.javaLabsJdkBuild()
                , Expect.javaLabsJdkLink()
            );
        }

        @Test
        void buildOpenJDK()
        {
            Asserts.steps2(
                jdkBuild(InMemoryFileSystem.empty(), Args.openJdkTree())
                , Expect.gitOpenJdkClone()
                , Expect.javaOpenJdkConfigure()
                , Expect.javaOpenJdkMake()
                , Expect.javaOpenJdkLink()
            );
        }

        @Test
        void skipBuildOpenJDK()
        {
            final var fs = InMemoryFileSystem.ofExists(
                Expect.jdkDownload().step
                , Expect.bootJdkExtract().step
                , Expect.gitOpenJdkClone().step
                , Expect.javaOpenJdkConfigure().step
                , Expect.javaOpenJdkMake().step
            );

            Asserts.steps2(
                jdkBuild(fs, Args.openJdkTree())
                , Expect.gitOpenJdkClone().untouched()
                , Expect.javaOpenJdkConfigure().untouched()
                , Expect.javaOpenJdkMake().untouched()
                , Expect.javaOpenJdkLink()
            );
        }

        @Test
        void skipBuildLabsJDK()
        {
            final var fs = InMemoryFileSystem.ofExists(
                Expect.jdkDownload().step
                , Expect.bootJdkExtract().step
                , Expect.gitLabsJdkClone().step
                , Expect.javaLabsJdkBuild().step
            );

            Asserts.steps2(
                jdkBuild(fs, Args.labsJdkTree())
                , Expect.gitLabsJdkClone().untouched()
                , Expect.javaLabsJdkBuild().untouched()
                , Expect.javaLabsJdkLink()
            );
        }

        static List<? extends Output> jdkClone(InMemoryFileSystem fs, String... extra)
        {
            final var cli = Cli.read(Lists.prepend("jdk-build", List.of(extra)));
            final var build = Jdk.Build.of(cli);
            // TODO Remove List.of when all return List
            return List.of(Jdk.clone(build.tree(), fs.exec()));
        }

        @Test
        void cloneMercurial()
        {
            Asserts.steps2(
                jdkClone(InMemoryFileSystem.empty(), Args.mercurialTree())
                , Expect.mercurialOpenJdkClone()
            );
        }

        static List<? extends Output> jdkGet(InMemoryFileSystem fs, OperatingSystem.Type osType)
        {
            return Jdk.get(
                new Jdk.Get(Jdk.JDK_11_0_7, Path.of("boot-jdk-11"))
                , fs.install()
                , fs.linking(osType)
            );
        }

        @Test
        void getBootJdkMacOs()
        {
            Asserts.steps2(
                jdkGet(InMemoryFileSystem.empty(), OperatingSystem.Type.MAC_OS)
                , Expect.jdkDownload()
                , Expect.bootJdkExtract()
                , Expect.bootJdkLinkMacOs()
            );
        }

        @Test
        void getBootJdk()
        {
            Asserts.steps2(
                jdkGet(InMemoryFileSystem.empty(), OperatingSystem.Type.UNKNOWN)
                , Expect.jdkDownload()
                , Expect.bootJdkExtract()
                , Expect.bootJdkLink()
            );
        }
    }

    static class CheckGraal
    {
        @Test
        void build()
        {
            Asserts.steps2(
                graalBuild(InMemoryFileSystem.empty())
                , Expect.gitMxClone()
                , Expect.gitGraalClone()
                , Expect.graalBuild()
                , Expect.graalLink()
            );
        }

        @Test
        void skipBuild()
        {
            final var fs = InMemoryFileSystem.ofExists(
                Expect.gitMxClone().step
                , Expect.gitGraalClone().step
                , Expect.graalBuild().step
            );
            Asserts.steps2(
                graalBuild(fs)
                , Expect.gitMxClone().untouched()
                , Expect.gitGraalClone().untouched()
                , Expect.graalBuild().untouched()
                , Expect.graalLink()
            );
        }

        static List<? extends Output> graalBuild(InMemoryFileSystem fs, String... extra)
        {
            final var cli = Cli.read(Lists.prepend("graal-build", List.of(extra)));
            return Graal.build(
                Graal.Build.of(cli)
                , fs.exec()
                , fs.linking()
                , Args.roots()
            );
        }
    }

    static class CheckMandrel
    {
        @Test
        void build()
        {
            Asserts.steps2(
                mandrelBuild(InMemoryFileSystem.empty())
                , Expect.gitMandrelClone()
                , Expect.gitMxClone()
                , Expect.gitMandrelPackagingClone()
                , Expect.mavenDownload()
                , Expect.mavenExtract()
                , Expect.mandrelBuild()
            );
        }

        @Test
        void skipBuild()
        {
            final var fs = InMemoryFileSystem.ofExists(
                Expect.gitMandrelClone().step
                , Expect.gitMxClone().step
                , Expect.gitMandrelPackagingClone().step
                , Expect.mavenDownload().step
                , Expect.mavenExtract().step
                , Expect.mandrelBuild().step
            );
            Asserts.steps2(
                mandrelBuild(fs)
                , Expect.gitMandrelClone().untouched()
                , Expect.gitMxClone().untouched()
                , Expect.gitMandrelPackagingClone().untouched()
                , Expect.mavenDownload().untouched()
                , Expect.mavenExtract().untouched()
                , Expect.mandrelBuild().untouched()
            );
        }

        static List<? extends Output> mandrelBuild(InMemoryFileSystem fs, String... extra)
        {
            final var cli = Cli.read(Lists.prepend("mandrel-build", List.of(extra)));
            return Mandrel.build(
                Mandrel.Build.of(cli)
                , fs.exec()
                , fs.install()
                , Args.roots()
            );
        }
    }

    // TODO Merge with CheckGraal
    @Deprecated
    static class CheckGraalGet
    {
        @Test
        void get()
        {
            final var fs = InMemoryFileSystem.empty();
            final var url = "https://doestnotexist.com/archive.tar.gz";

            Asserts.steps(
                GraalGet.Graal.get(cli("--url", url), fs.exec(), fs.install())
                , Expect.download(url, "downloads/archive.tar.gz")
                , Expect.extract("downloads/archive.tar.gz", "graalvm/graal")
            );
        }

        @Test
        void getAndDownloadNativeImage()
        {
            final var fs = InMemoryFileSystem.empty();
            final var url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz";

            Asserts.steps(
                GraalGet.Graal.get(cli("--url", url), fs.exec(), fs.install())
                , Expect.download(url, "downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz")
                , Expect.extract("downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz", "graalvm/graal")
                , Expect.guNativeImage()
            );
        }

        @Test
        void skipBothDownloadAndExtract()
        {
            final var url = "https://skip.both/download";
            final var downloadPath = "downloads/download";
            final var extractPath = "graalvm/graal";

            final var fs = InMemoryFileSystem.ofExists(
                Expect.download(url, downloadPath).step
                , Expect.extract(downloadPath, extractPath).step
            );

            Asserts.steps(
                GraalGet.Graal.get(cli("--url", url), fs.exec(), fs.install())
                , Expect.download(url, downloadPath).untouched()
                , Expect.extract(downloadPath, extractPath).untouched()
            );
        }

        @Test
        void skipOnlyDownload()
        {
            final var url = "https://skip.only/download.tar.gz";
            final var path = "downloads/download.tar.gz";

            final var fs = InMemoryFileSystem.ofExists(
                Expect.download(url, path).step
            );

            Asserts.steps(
                GraalGet.Graal.get(cli("--url", url), fs.exec(), fs.install())
                , Expect.download(url, path).untouched()
                , Expect.extract(path, "graalvm/graal")
            );
        }

        @Test
        void missingURL()
        {
            final var exception = assertThrows(
                IllegalArgumentException.class
                , () -> GraalGet.Options.of(Cli.read(List.of()))
            );

            assertThat(exception.getMessage(), is("Not enough arguments"));
        }

        private static GraalGet.Options cli(String... extra)
        {
            return GraalGet.Options.of(
                Cli.read(Lists.prepend("graal-get", Arrays.asList(extra)))
            );
        }
    }

    static class CheckMavenBuild
    {
        @Test
        void quarkus()
        {
            final var fs = InMemoryFileSystem.empty();
            final var tree = "https://github.com/quarkusio/quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("--tree", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("quarkus")
            );
        }

        @Test
        void skipQuarkus()
        {
            final var fs = InMemoryFileSystem.ofExists(Expect.mavenBuild("quarkus").step);
            final var tree = "https://github.com/quarkusio/quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("--tree", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("quarkus").untouched()
            );
        }

        @Test
        void camelQuarkus()
        {
            final var fs = InMemoryFileSystem.empty();
            final var tree = "https://github.com/apache/camel-quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("--tree", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("camel-quarkus", "-Dquarkus.version=999-SNAPSHOT")
            );
        }

        @Test
        void camel()
        {
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--tree"
                , "https://github.com/apache/camel/tree/master"
                , "--additional-build-args"
                , "-Pfastinstall"
                , "-Pdoesnotexist"
            );
            Asserts.step(
                MavenBuild.build(options, fs.exec(), Args.roots())
                , Expect.mavenBuild(
                    "camel"
                    , "-Pfastinstall"
                    , "-Pdoesnotexist"
                )
            );
        }

        private static MavenBuild.Options cli(String... extra)
        {
            return MavenBuild.Options.of(
                Cli.read(Lists.prepend("maven-build", Arrays.asList(extra)))
            );
        }
    }

    static class CheckMavenTest
    {
        @Test
        void cliAdditionalTestArgsOptions()
        {
            assertThat(
                cli(
                    "--suite", "ignore"
                    , "--additional-test-args", "b", ":c", "-y", "-Dx=w"
                ).additionalTestArgs()
                , is(equalTo(
                    List.of(
                        "b", ":c", "-y", "-Dx=w"
                    )
                ))
            );
        }

        @Test
        void extraArguments()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli(
                "--suite", "suite-a"
                , "--additional-test-args", "p1", ":p2", ":p3", "-p4"
            );
            MavenTest.test(options, os::record, Args.roots());

            os.assertNumberOfTasks(1);
            os.assertTask(t ->
                assertThat(
                    String.join(" ", t.args())
                    , is(equalTo("/home/maven/bin/mvn install -Dnative -Dformat.skip p1 :p2 :p3 -p4"))
                )
            );
        }

        @Test
        void suite()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("--suite", "suite-a");
            MavenTest.test(options, os::record, Args.roots());

            os.assertNumberOfTasks(1);
            os.assertAllTasks(t -> assertThat(t.args().stream().findFirst(), is(Optional.of("/home/maven/bin/mvn"))));
            os.assertTask(t -> assertThat(t.directory(), is(Path.of("suite-a"))));
        }

        @Test
        void quarkus()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("--suite", "quarkus");
            MavenTest.test(options, os::record, Args.roots());

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("/home/maven/bin/mvn")));
                assertThat(task.directory(), is(Path.of("quarkus/integration-tests")));
            });
        }

        @Test
        void quarkusPlatform()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("--suite", "quarkus-platform");
            MavenTest.test(options, os::record, Args.roots());

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("quarkus-platform")));
                final var arguments = new ArrayList<>(task.args());
                assertThat(arguments.stream().findFirst(), is(Optional.of("/home/maven/bin/mvn")));
                assertThat(
                    arguments.stream().filter(e -> e.equals("-Dquarkus.version=999-SNAPSHOT")).findFirst()
                    , is(Optional.of("-Dquarkus.version=999-SNAPSHOT"))
                );
                assertThat(
                    arguments.stream().filter(e -> e.equals("-Dcamel-quarkus.version=1.1.0-SNAPSHOT")).findFirst()
                    , is(Optional.of("-Dcamel-quarkus.version=1.1.0-SNAPSHOT"))
                );
            });
        }

        private static MavenTest.Options cli(String... extra)
        {
            return MavenTest.Options.of(
                Cli.read(Lists.prepend("maven-test", Arrays.asList(extra)))
            );
        }
    }

    private static final class InMemoryFileSystem
    {
        final Map<Path, Boolean> exists;

        private InMemoryFileSystem(Map<Path, Boolean> exists)
        {
            this.exists = exists;
        }

        // TODO Make private
        boolean exists(Path path)
        {
            final var doesExist = exists.get(path);
            return doesExist == null ? false : doesExist;
        }

        boolean touch(Marker marker)
        {
            return true;
        }

        Steps.Exec.Effects exec()
        {
            return new Steps.Exec.Effects(this::exists, e -> {}, this::touch);
        }

        Steps.Install.Effects install()
        {
            return new Steps.Install.Effects(
                new Steps.Download.Effects(this::exists, this::touch, d -> {}, () -> OperatingSystem.Type.MAC_OS)
                , new Steps.Extract.Effects(exec(), p -> {})
            );
        }

        Steps.Linking.Effects linking(OperatingSystem.Type osType)
        {
            return new Steps.Linking.Effects(Link::new, () -> osType);
        }

        Steps.Linking.Effects linking()
        {
            return new Steps.Linking.Effects(Link::new, () -> OperatingSystem.Type.UNKNOWN);
        }

        static InMemoryFileSystem empty()
        {
            return new InMemoryFileSystem(Collections.emptyMap());
        }

        // TODO take Expect instead of Step
        static InMemoryFileSystem ofExists(Step... steps)
        {
            final var map = Stream.of(steps)
                .map(Marker::of)
                .map(Marker::path)
                .collect(
                    Collectors.toMap(Function.identity(), marker -> true)
                );
            return new InMemoryFileSystem(map);
        }
    }

    // TODO remove and rely solely on returned markers
    private static final class RecordingOperatingSystem
    {
        private final Queue<Object> tasks = new ArrayDeque<>();

        static RecordingOperatingSystem macOS()
        {
            return new RecordingOperatingSystem();
        }

        void record(Steps.Exec task)
        {
            offer(task);
        }

        private void offer(Object task)
        {
            final var success = tasks.offer(task);
            assertThat(success, is(true));
        }

        // TODO rename to assertOneTask
        void assertExecutedOneTask(Object expected, Marker result)
        {
            assertNumberOfTasks(1);
            assertExecutedTask(expected, result);
        }

        // TODO rename to assertTask
        void assertExecutedTask(Object expected, Marker result)
        {
            final var actual = peekTask();
            assertThat(actual, is(expected));
            assertThat(result.touched(), is(true));
            assertThat(
                result.path()
                , is(Path.of(format("%s.marker", Hashing.sha1(expected.toString()))))
            );
            forward();
        }

        void assertNoTask(Marker result)
        {
            assertNumberOfTasks(0);
            assertThat(result.exists(), is(true));
            assertThat(result.touched(), is(false));
        }

        void assertNumberOfTasks(int size)
        {
            assertThat(tasks.toString(), tasks.size(), is(size));
        }

        @Deprecated
        void assertTask(Consumer<Steps.Exec> asserts)
        {
            final Steps.Exec head = peekTask();
            asserts.accept(head);
        }

        @Deprecated
        void assertAllTasks(Consumer<Steps.Exec> asserts)
        {
            for (Object object : tasks)
            {
                if (object instanceof Steps.Exec task)
                {
                    asserts.accept(task);
                }
            }
        }

        void forward()
        {
            tasks.poll();
        }

        @SuppressWarnings("unchecked")
        private <T> T peekTask()
        {
            return (T) tasks.peek();
        }
    }

    static final class Asserts
    {
        static void step(Marker actual, Expect expected)
        {
            assertThat(actual.cause(), is(expected.step.toString()));
            assertThat(actual.exists(), is(true));
            assertThat(actual.touched(), is(expected.touched));
            assertThat(
                actual.path().toString()
                , is(format("%s.marker", Hashing.sha1(expected.step.toString())))
            );
        }

        static void step(Link actual, Expect expected)
        {
            // TODO avoid cast? the only step that can produce a Link is Linking...
            final var linking = (Steps.Linking) expected.step;
            assertThat(actual.link(), is(linking.link()));
            assertThat(actual.target(), is(linking.target()));
        }

        @Deprecated // use steps2
        static void steps(List<Marker> markers, Expect... expects)
        {
            assertThat(markers.toString(), markers.size(), is(expects.length));
            for (int i = 0; i < expects.length; i++)
            {
                step(markers.get(i), expects[i]);
            }
        }

        static void steps2(List<? extends Output> outputs, Expect... expects)
        {
            assertThat(outputs.toString(), outputs.size(), is(expects.length));
            for (int i = 0; i < expects.length; i++)
            {
                final Output output = outputs.get(i);
                if (output instanceof Marker marker)
                {
                    step(marker, expects[i]);
                }
                else if (output instanceof Link link)
                {
                    step(link, expects[i]);
                }
                else
                {
                    throw new IllegalStateException(String.format(
                        "Unknown output implementation for: %s"
                        , output
                    ));
                }
            }
        }
    }

    record Expect(Step step, boolean touched)
    {
        Expect untouched()
        {
            return new Expect(step, false);
        }

        static Expect of(Step step)
        {
            return new Expect(step, true);
        }

        static Expect gitOpenJdkClone()
        {
            return gitClone("openjdk/jdk11u-dev", "master");
        }

        static Expect gitLabsJdkClone()
        {
            return Expect.of(Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "jvmci-20.2-b02"
                , "--depth"
                , "20"
                , "https://github.com/graalvm/labs-openjdk-11"
            ));
        }

        static Expect gitMxClone()
        {
            return gitClone("graalvm/mx", "master");
        }

        static Expect gitGraalClone()
        {
            return gitClone("oracle/graal", "master");
        }

        static Expect gitMandrelClone()
        {
            return gitClone("graalvm/mandrel", "mandrel/20.1");
        }

        static Expect gitMandrelPackagingClone()
        {
            return gitClone("graalvm/mandrel-packaging", "master");
        }

        private static Expect gitClone(String repo, String branch)
        {
            return Expect.of(Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , branch
                , "--depth"
                , "1"
                , String.format("https://github.com/%s", repo)
            ));
        }

        static Expect javaOpenJdkConfigure()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                , "--with-extra-cflags=-fcommon"
                , "--enable-aot=no"
                , "--with-boot-jdk=/home/bootjdk_home"
            ));
        }

        static Expect javaOpenJdkMake()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "make"
                , "graal-builder-image"
            ));
        }

        static Expect javaOpenJdkLink()
        {
            return Expect.of(new Steps.Linking(
                Path.of("java_home")
                , Path.of("jdk11u-dev/build/graal-server-release/images/graal-builder-jdk")
            ));
        }

        static Expect javaLabsJdkBuild()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("labs-openjdk-11")
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , "/home/bootjdk_home"
            ));
        }

        static Expect javaLabsJdkLink()
        {
            return Expect.of(new Steps.Linking(
                Path.of("java_home")
                , Path.of("labs-openjdk-11/java_home")
            ));
        }

        static Expect mandrelBuild()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("mandrel-packaging")
                , List.of(
                    EnvVar.of("JAVA_HOME", "/today/java_home")
                    , EnvVar.of("MX_HOME", "/today/mx")
                    , EnvVar.of("MANDREL_REPO", "/today/mandrel")
                    , EnvVar.of("MANDREL_HOME", "/today/graalvm_home")
                    , EnvVar.of("MAVEN_HOME", "/home/maven")
                )
                , "./buildJDK.sh"
            ));
        }

        static Expect graalBuild()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("graal", "substratevm")
                , "/today/mx/mx"
                , "--java-home"
                , "/today/java_home"
                , "build"
            ));
        }

        static Expect graalLink()
        {
            return Expect.of(new Steps.Linking(
                Path.of("graalvm_home")
                , Path.of("graal", "sdk", "latest_graalvm_home")
            ));
        }

        static Expect mavenBuild(String path, String... extra)
        {
            final var args = Stream.concat(
                Stream.of(
                    "/home/maven/bin/mvn"
                    , "install"
                    , "-DskipTests"
                    , "-DskipITs"
                    , "-Denforcer.skip"
                    , "-Dformat.skip"
                )
                , Stream.of(extra)
            );

            return Expect.of(Steps.Exec.of(
                Path.of(path)
                , List.of(new EnvVar("JAVA_HOME", Path.of("/", "today", "graalvm_home")))
                , args.toArray(String[]::new)
            ));
        }

        static Expect mavenDownload()
        {
            return download(
                "https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz"
                , "downloads/apache-maven-3.6.3-bin.tar.gz"
            );
        }

        static Expect jdkDownload()
        {
            return download(
                "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
                , "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
            );
        }

        static Expect download(String url, String path)
        {
            return Expect.of(new Steps.Download(
                URLs.of(url)
                , Path.of(path)
            ));
        }

        static Expect mavenExtract()
        {
            return extract("downloads/apache-maven-3.6.3-bin.tar.gz", "maven");
        }

        static Expect bootJdkExtract()
        {
            return extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz", "boot-jdk-11");
        }

        static Expect extract(String tar, String path)
        {
            return Expect.of(Steps.Exec.of(
                Path.of("")
                , "tar"
                , "-xzvpf"
                , tar
                , "-C"
                , path
                , "--strip-components"
                , "1"
            ));
        }

        static Expect bootJdkLinkMacOs()
        {
            return Expect.of(new Steps.Linking(
                Path.of("bootjdk_home")
                , Path.of("boot-jdk-11/Contents/Home")
            ));
        }

        static Expect bootJdkLink()
        {
            return Expect.of(new Steps.Linking(
                Path.of("bootjdk_home")
                , Path.of("boot-jdk-11")
            ));
        }

        static Expect guNativeImage()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("graal/bin")
                , "./gu"
                , "install"
                , "native-image"
            ));
        }

        static Expect mercurialOpenJdkClone()
        {
            return Expect.of(Steps.Exec.of(
                "hg"
                , "clone"
                , "http://hg.openjdk.java.net/jdk8/jdk8"
            ));
        }
    }

    static final class Args
    {
        static String[] openJdkTree()
        {
            return new String[]{
                "--tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            };
        }

        static String[] mercurialTree()
        {
            return new String[]{
                "--tree",
                "http://hg.openjdk.java.net/jdk8/jdk8"
            };
        }

        static String[] labsJdkTree()
        {
            return new String[]{
                "--tree"
                , "https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02"
            };
        }

        static Roots roots()
        {
            return new Roots(
                p -> Path.of("/", "home").resolve(p)
                , p -> Path.of("/", "today").resolve(p)
            );
        }
    }

    public static void main(String[] args)
    {
        Check.check();
    }
}

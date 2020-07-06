//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.2.0
//DEPS org.apache.logging.log4j:log4j-core:2.13.0
//DEPS org.hamcrest:hamcrest:2.2
//DEPS org.junit.jupiter:junit-jupiter-engine:5.6.1
//DEPS org.junit.platform:junit-platform-launcher:1.6.1

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

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
import java.util.Collections;
import java.util.Comparator;
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
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Command
public class qollider implements Runnable
{
    static final Logger LOG = LogManager.getLogger(qollider.class);

    @Spec
    CommandSpec spec;

    public static void main(String[] args)
    {
        // TODO improve logging by only log 2 things: the inputs and the resulting outputs/effects
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.DEBUG);

        // Run checks
        QuarkusCheck.check();

        final var today = FileSystem.ofToday();
        final var home = FileSystem.ofHome();

        // TODO consider collapsing graal-build and maven-build into build
        // depending on the tree passed in, the code could multiplex.
        // Downside is that you loose the ability to have defaults for graal-build (better be explicit? less maintenance of code)
        final var graalBuild = GraalBuild.ofSystem(home, today);
        final var graalGet = GraalGet.ofSystem(today);
        final var mavenBuild = MavenBuild.ofSystem(home, today);
        final var mavenTest = MavenTest.ofSystem(home, today);

        final var cli = Cli.of(
            graalBuild
            , graalGet
            , mavenBuild
            , mavenTest
        );
        final var result = cli.execute(args);
        if (0 == result.exitCode())
        {
            LOG.info("Execution summary:");
            LOG.info("Inputs:{}{}"
                , System.lineSeparator()
                , List.of(args).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(System.lineSeparator()))
            );
            LOG.info("Outputs:{}{}"
                , System.lineSeparator()
                , result.outputs().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(System.lineSeparator()))
            );
        }
        else
        {
            LOG.error("Failed executing: {}", result);
        }
        System.exit(result.exitCode());
    }

    @Override
    public void run()
    {
        throw new ParameterException(
            spec.commandLine()
            , "Missing required subcommand"
        );
    }
}

final class Cli
{
    final CommandLine picoCli;

    private Cli(CommandLine picoCli)
    {
        this.picoCli = picoCli;
    }

    Result execute(String... args)
    {
        final var exitCode = picoCli.execute(args);
        ParseResult parseResult = picoCli.getParseResult();
        CommandLine sub = parseResult.subcommand().commandSpec().commandLine();
        return new Result(exitCode, sub.getExecutionResult());
    }

    static Cli of(Object... subcommands)
    {
        final var cmdline = new CommandLine(new qollider());
        // Sub-commands need to be added first,
        // for converters and other options to have effect
        Arrays.asList(subcommands).forEach(cmdline::addSubcommand);
        return new Cli(
            cmdline
                .registerConverter(Repository.class, Repository::of)
                .setCaseInsensitiveEnumValuesAllowed(true)
        );
    }

    record Result(int exitCode, List<?> outputs) {}
}

@Command(
    name = "graal-get"
    , aliases = {"gg"}
    , description = "Get Graal."
    , mixinStandardHelpOptions = true
)
class GraalGet implements Callable<List<?>>
{
    private final Function<Options, List<?>> runner;

    @Option(
        description = "URL of distribution"
        , names =
        {
            "-u"
            , "--url"
        }
        , required = true
    )
    URL url;

    private GraalGet(Function<Options, List<?>> runner)
    {
        this.runner = runner;
    }

    static GraalGet ofSystem(FileSystem fs)
    {
        return new GraalGet(new GraalGet.GetRunner(fs));
    }

    static GraalGet of(Function<Options, List<?>> runner)
    {
        return new GraalGet(runner);
    }

    @Override
    public List<?> call()
    {
        final var options = new Options(url, Path.of("graalvm", "graal"));
        return runner.apply(options);
    }

    record GetRunner(FileSystem fs) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
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

    record Options(URL url, Path graal) {}
}

@Command(
    name = "graal-build"
    , aliases = {"gb"}
    , description = "Build Graal."
    , mixinStandardHelpOptions = true
)
class GraalBuild implements Callable<List<?>>
{
    private final Function<Options, List<?>> runner;

    @Option(
        defaultValue = "https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02"
        , description = "JDK source tree URL"
        , names =
        {
            "-jt"
            , "--jdk-tree"
        }
    )
    Repository jdkTree;

    @Option(
        defaultValue = "https://github.com/graalvm/mx/tree/master"
        , description = "mx source tree URL"
        , names =
        {
            "-mt"
            , "--mx-tree"
        }
    )
    Repository mxTree;

    @Option(
        defaultValue = "https://github.com/oracle/graal/tree/master"
        , description = "Graal source tree URL"
        , names =
        {
            "-gt"
            , "--graal-tree"
        }
    )
    Repository graalTree;

    @Option(
        defaultValue = "https://github.com/graalvm/mandrel-packaging/tree/master"
        , description = "Mandrel packaging source tree URL"
        , names =
        {
            "-pt"
            , "--packaging-tree"
        }
    )
    Repository packagingTree;

    private GraalBuild(Function<Options, List<?>> runner)
    {
        this.runner = runner;
    }

    static GraalBuild ofSystem(FileSystem home, FileSystem today)
    {
        return new GraalBuild(new BuildRunner(home, today));
    }

    static GraalBuild of(Function<Options, List<?>> runner)
    {
        return new GraalBuild(runner);
    }

    @Override
    public List<?> call()
    {
        final var options = new Options(jdkTree, mxTree, graalTree, packagingTree);
        return runner.apply(options);
    }

    record BuildRunner(FileSystem home, FileSystem today) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
        {
            final var osToday = OperatingSystem.of(today);
            final var osHome = OperatingSystem.of(home);
            final var roots = new Roots(home::resolve, today::resolve);

            final var execToday = Steps.Exec.Effects.of(osToday);
            final var installHome = Steps.Install.Effects.of(Web.of(home), osHome);

            return Lists.flatten(
                Java.clone(options, execToday)
                , Git.clone(options.mx, execToday)
                , Git.clone(options.graal, execToday)
                , Java.install(options, installHome)
                , Java.build(options, execToday, osToday::type, roots)
                , Java.link(options, today::symlink)
                , Graal.build(options, execToday, installHome, roots)
                , Graal.link(options, today::symlink, osToday.fs::exists)
            );
        }
    }

    record Options(
        Repository jdk
        , Repository mx
        , Repository graal
        , Repository packaging
    )
    {
        Java.Type javaType()
        {
            return Java.type(jdk);
        }

        Graal.Type graalType(Predicate<Path> exists)
        {
            return Graal.type(graal, exists);
        }

        Path mxPath()
        {
            return mxHome().resolve("mx");
        }

        Path mxHome()
        {
            return Path.of(mx.name());
        }

        Path bootJdkPath()
        {
            return Path.of("boot-jdk-11");
        }

        Path bootJdkHome(Supplier<OperatingSystem.Type> osType)
        {
            final var root = bootJdkPath();
            return osType.get().isMac()
                ? root.resolve(Path.of("Contents", "Home"))
                : root;
        }
    }

    static final class Java
    {
        static Marker clone(Options options, Steps.Exec.Effects effects)
        {
            return switch (options.jdk.type())
            {
                case GIT ->
                    Git.clone(
                        options.jdk
                        , effects
                    );
                case MERCURIAL ->
                    Mercurial.clone(options.jdk, effects);
            };
        }

        static List<Marker> build(
            Options options
            , Steps.Exec.Effects effects
            , Supplier<OperatingSystem.Type> osType
            , Roots roots
        )
        {
            final var bootJdkHome = roots.home().apply(options.bootJdkHome(osType));
            final var tasks = switch (options.javaType())
                {
                    case OPENJDK -> Java.OpenJDK.buildSteps(options, bootJdkHome);
                    case LABSJDK -> Java.LabsJDK.buildSteps(options, bootJdkHome);
                };

            return tasks
                .map(t -> Steps.Exec.run(t, effects))
                .collect(Collectors.toList());
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var jdkPath = Path.of(options.jdk.name());
            final var target =
                switch (options.javaType())
                    {
                        case OPENJDK -> Java.OpenJDK.javaHome(jdkPath);
                        case LABSJDK -> Java.LabsJDK.javaHome(jdkPath);
                    };

            final var link = Homes.java();
            return symLink.apply(link, target);
        }

        // TODO create a record to capture Java version info (major, minor, micro, build)
        static List<Marker> install(Options options, Steps.Install.Effects install)
        {
            final var javaVersionMajor = "11";
            final var javaVersionBuild = "10";
            final var javaVersion = format("%s.0.7", javaVersionMajor);
            final var javaBaseUrl = format(
                "https://github.com/AdoptOpenJDK/openjdk%s-binaries/releases/download"
                , javaVersionMajor
            );

            final var osType = install.download().osType().get();
            final var javaOsType = osType.isMac() ? "mac" : osType;
            final var arch = "x64";

            final var url = URLs.of(
                "%s/jdk-%s%%2B%s/OpenJDK%sU-jdk_%s_%s_hotspot_%s_%s.tar.gz"
                , javaBaseUrl
                , javaVersion
                , javaVersionBuild
                , javaVersionMajor
                , arch
                , javaOsType
                , javaVersion
                , javaVersionBuild
            );

            return Steps.Install.install(new Steps.Install(url, options.bootJdkPath()), install);
        }

        private static final class OpenJDK
        {
            static Stream<Steps.Exec> buildSteps(Options options, Path bootJdkHome)
            {
                return Stream.of(
                    configure(options, bootJdkHome)
                    , make(options)
                );
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

            private static Steps.Exec configure(Options options, Path bootJdkHome)
            {
                return Steps.Exec.of(
                    Path.of(options.jdk.name())
                    , "bash"
                    , "configure"
                    , "--with-conf-name=graal-server-release"
                    , "--disable-warnings-as-errors"
                    , "--with-jvm-features=graal"
                    , "--with-jvm-variants=server"
                    // Workaround for https://bugs.openjdk.java.net/browse/JDK-8235903 on newer GCC versions
                    , "--with-extra-cflags=-fcommon"
                    , "--enable-aot=no"
                    , format("--with-boot-jdk=%s", bootJdkHome.toString())
                );
            }

            private static Steps.Exec make(Options options)
            {
                return Steps.Exec.of(
                    Path.of(options.jdk.name())
                    , "make"
                    , "graal-builder-image"
                );
            }
        }

        private static final class LabsJDK
        {
            static Stream<Steps.Exec> buildSteps(Options options, Path bootJdkHome)
            {
                return Stream.of(buildJDK(options, bootJdkHome));
            }

            static Path javaHome(Path jdk)
            {
                return jdk.resolve("java_home");
            }

            private static Steps.Exec buildJDK(Options options, Path bootJdkHome)
            {
                return Steps.Exec.of(
                    Path.of(options.jdk.name())
                    , "python"
                    , "build_labsjdk.py"
                    , "--boot-jdk"
                    , bootJdkHome.toString()
                );
            }
        }

        static Java.Type type(Repository repo)
        {
            return repo.name().startsWith("labs") ? Type.LABSJDK : Type.OPENJDK;
        }

        enum Type
        {
            OPENJDK
            , LABSJDK
        }
    }

    static final class Graal
    {
        static List<Marker> build(Options options, Steps.Exec.Effects exec, Steps.Install.Effects install, Roots roots)
        {
            final var type = options.graalType(exec.exists());
            return switch (type)
            {
                case MANDREL -> Mandrel.build(options, exec, install, roots);
                case ORACLE -> List.of(Oracle.build(options, exec, roots));
            };
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink, Predicate<Path> exists)
        {
            final var type = options.graalType(exists);
            return switch (type)
            {
                case MANDREL -> new Link(Homes.graal(), Homes.graal());
                case ORACLE -> Oracle.link(options, symLink);
            };
        }

        static final class Mandrel
        {
            static List<Marker> build(Options options, Steps.Exec.Effects exec, Steps.Install.Effects install, Roots roots)
            {
                final var cloneMarker = Git.clone(options.packaging, exec);
                final var mavenInstallMarkers = Maven.install(install);

                final var today = roots.today();
                final var buildMarker = Steps.Exec.run(
                    Steps.Exec.of(
                        Path.of("mandrel-packaging")
                        , List.of(
                            EnvVar.javaHome(today.apply(Homes.java()))
                            , EnvVar.of("MX_HOME", today.apply(options.mxHome()))
                            , EnvVar.of("MANDREL_REPO", today.apply(Path.of(options.graal.name())))
                            , EnvVar.of("MANDREL_HOME", today.apply(Homes.graal()))
                            , EnvVar.of("MAVEN_HOME", Maven.home(roots.home()))
                        )
                        , "./buildJDK.sh"
                    )
                    , exec
                );

                // TODO Create Lists.flatten(List<E>...)
                final var result = Lists.mutable(cloneMarker);
                result.addAll(mavenInstallMarkers);
                result.add(buildMarker);
                return result;
            }
        }

        static final class Oracle
        {
            static Marker build(Options options, Steps.Exec.Effects effects, Roots roots)
            {
                final var svm = Path.of(options.graal.name(), "substratevm");
                final var today = roots.today();
                return Steps.Exec.run(
                    Steps.Exec.of(
                        svm
                        , today.apply(options.mxPath()).toString()
                        , "--java-home"
                        , today.apply(Homes.java()).toString()
                        , "build"
                    )
                    , effects
                );
            }

            static Link link(Options options, BiFunction<Path, Path, Link> symLink)
            {
                final var target = Path.of(
                    options.graal.name()
                    ,"sdk"
                    , "latest_graalvm_home"
                );

                return symLink.apply(Homes.graal(), target);
            }
        }

        static Graal.Type type(Repository repo, Predicate<Path> exists)
        {
            return switch (repo.name())
            {
                case "mandrel" ->
                    exists.test(Path.of("mandrel", "README-Mandrel.md"))
                        ? Type.MANDREL
                        : Type.ORACLE;
                case "graal" -> Type.ORACLE;
                default -> throw Illegal.value(repo.name());
            };
        }

        enum Type
        {
            MANDREL
            , ORACLE
        }
    }
}

@Command(
    name = "maven-build"
    , aliases = {"mb"}
    , description = "Maven build."
    , mixinStandardHelpOptions = true
)
class MavenBuild implements Callable<List<?>>
{
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

    private final Function<Options, List<?>> runner;

    @Option(
        description = "Source tree URL"
        , names =
        {
            "-t"
            , "--tree"
        }
        , required = true
    )
    Repository tree;

    @Option(
        description = "Additional build arguments"
        , names =
        {
            "-aba"
            , "--additional-build-args"
        }
        , split = ","
    )
    List<String> additionalBuildArgs = new ArrayList<>();

    private MavenBuild(Function<Options, List<?>> runner)
    {
        this.runner = runner;
    }

    static MavenBuild ofSystem(FileSystem home, FileSystem today)
    {
        return new MavenBuild(new BuildRunner(home, today));
    }

    static MavenBuild of(Function<Options, List<?>> runner)
    {
        return new MavenBuild(runner);
    }

    @Override
    public List<?> call()
    {
        final var options = new Options(tree, additionalBuildArgs);
        return runner.apply(options);
    }

    record BuildRunner(FileSystem home, FileSystem today) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
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
    }

    record Options(Repository tree, List<String> additionalBuildArgs)
    {
        Path project()
        {
            return Path.of(tree.name());
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

@Command(
    name = "maven-test"
    , aliases = {"mt"}
    , description = "Maven test."
    , mixinStandardHelpOptions = true
)
class MavenTest implements Callable<List<?>>
{
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

    private final Function<Options, List<?>> runner;

    @Option(
        description = "Test suite to run"
        , names =
        {
            "-s"
            , "--suite"
        }
        , required = true
    )
    String suite;

    @Option(
        description = "Additional test arguments, each argument separated by comma (',')"
        , names =
        {
            "-ata"
            , "--additional-test-args"
        }
        , split = ","
    )
    final List<String> additionalTestArgs = new ArrayList<>();

    private MavenTest(Function<Options, List<?>> runner)
    {
        this.runner = runner;
    }

    static MavenTest ofSystem(FileSystem home, FileSystem today)
    {
        return new MavenTest(new TestRunner(home, today));
    }

    static MavenTest of(Function<Options, List<?>> runner)
    {
        return new MavenTest(runner);
    }

    @Override
    public List<?> call()
    {
        var options = new Options(suite, additionalTestArgs);
        return runner.apply(options);
    }

    record TestRunner(FileSystem home, FileSystem today) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
        {
            final var os = OperatingSystem.of(today);
            MavenTest.test(options, os::exec, new Roots(home::resolve, today::resolve));
            // TODO make it return markers so that it can be tested just like other commands
            return List.of();
        }
    }

    record Options(String suite, List<String> additionalTestArgs) {}

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

final class Homes
{
    static Path java()
    {
        return Path.of("java_home");
    }

    static Path graal()
    {
        return Path.of("graalvm_home");
    }
}

// Boundary value
record Marker(boolean exists, boolean touched, Path path, String info)
{
    Marker query(Predicate<Path> existsFn)
    {
        final var exists = existsFn.test(this.path);
        if (exists)
        {
            return new Marker(true, touched, path, info);
        }

        return new Marker(false, touched, path, info);
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
            return new Marker(true, true, path, info);
        }

        return new Marker(false, false, path, info);
    }

    static Marker of(Step step)
    {
        final var info = step.toString();
        final var hash = Hashing.sha1(info);
        final var path = Path.of(format("%s.marker", hash));
        return new Marker(false, false, path, info);
    }
}

// Boundary value
record Link(Path link, Path target) {}

// Dependency
class FileSystem
{
    final Path root;

    static FileSystem ofToday()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("ddMM");
        var today = date.format(formatter);
        var baseDir = Path.of(
            System.getProperty("user.home")
            , ".qollider"
            , "cache"
        );
        final var path = baseDir.resolve(today);
        return FileSystem.of(path);
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
            Files.writeString(path, marker.info());
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
            if (Files.exists(link))
                Files.delete(link);

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
    static final Logger LOG = LogManager.getLogger(qollider.class);

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
        LOG.debug(
            "Execute {} in {} with environment variables {}"
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
        , MACOSX
        , LINUX
        , OTHER
        ;

        boolean isMac()
        {
            return this == MACOSX;
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
            return Type.MACOSX;

        if (OS.contains("win"))
            return Type.WINDOWS;

        if (OS.contains("nux"))
            return Type.LINUX;

        return Type.OTHER;
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
    static final Logger LOG = LogManager.getLogger(qollider.class);

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
                    LOG.info(
                        "Download progress {} received"
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
    static <E> List<E> append(E element, List<E> list)
    {
        final var result = new ArrayList<>(list);
        result.add(element);
        return Collections.unmodifiableList(result);
    }

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

final class QuarkusCheck
{
    static void check()
    {
        // TODO remove summary listener, a bit too noisy
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectClass(CheckGit.class)
                , selectClass(CheckGraalBuild.class)
                , selectClass(CheckGraalGet.class)
                , selectClass(CheckMavenBuild.class)
                , selectClass(CheckMavenTest.class)
            )
            .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        // TODO only log first line
        listener.getSummary().printTo(new PrintWriter(System.out));
        listener.getSummary().printFailuresTo(new PrintWriter(System.err));
        final var failureCount = listener.getSummary().getTestsFailedCount();
        if (failureCount > 0)
        {
            throw new AssertionError(format(
                "Number of failed tests: %d"
                , failureCount
            ));
        }
    }

    private static class LoggingExtension implements BeforeAllCallback
    {
        @Override
        public void beforeAll(ExtensionContext extensionContext)
        {
            Configurator.initialize(new DefaultConfiguration());
            Configurator.setRootLevel(Level.DEBUG);
        }
    }

    @ExtendWith(LoggingExtension.class)
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

    @ExtendWith(LoggingExtension.class)
    static class CheckGraalBuild
    {
        @Test
        void javaLabsJDK()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli();

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type, Args.roots());
            final var marker = markers.get(0);
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("python")));
                assertThat(task.directory(), is(Path.of("labs-openjdk-11")));
            });
        }

        @Test
        void javaOpenJDK()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type, Args.roots());
            assertThat(markers.get(0).touched(), is(true));
            assertThat(markers.get(1).touched(), is(true));
            os.assertNumberOfTasks(2);
            os.assertExecutedTask(Expect.javaOpenJDKConfigure().step, markers.get(0));
            os.assertExecutedTask(Expect.javaOpenJDKMake().step, markers.get(1));
        }

        @Test
        void skipJavaOpenJDK()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var configure = Expect.javaOpenJDKConfigure();
            final var make = Expect.javaOpenJDKMake();
            final var fs = InMemoryFileSystem.ofExists(configure.step, make.step);
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type, Args.roots());
            os.assertNumberOfTasks(0);
            final var configureMarker = markers.get(0);
            assertThat(configureMarker.exists(), is(true));
            assertThat(configureMarker.touched(), is(false));
            final var makeMarker = markers.get(1);
            assertThat(makeMarker.exists(), is(true));
            assertThat(makeMarker.touched(), is(false));
        }

        @Test
        void skipJavaLabsJDK()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var configure = Steps.Exec.of(
                Path.of("labs-openjdk-11")
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , "/home/boot-jdk-11/Contents/Home"
            );
            final var fs = InMemoryFileSystem.ofExists(configure);
            final var options = cli();

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type, Args.roots());
            os.assertNumberOfTasks(0);
            final var marker = markers.get(0);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
        }

        @Test
        void graalLink()
        {
            final var options = cli();
            final var linked = GraalBuild.Graal.link(
                options
                , Link::new
                , path -> true
            );
            assertThat(linked.link(), is(Homes.graal()));
        }

        @Test
        void graalOracleBuild()
        {
            final var fs = InMemoryFileSystem.empty();
            Asserts.steps(
                GraalBuild.Graal.build(cli(), fs.exec(), fs.install(), Args.roots())
                , Expect.graalOracleBuild()
            );
        }

        @Test
        void graalMandrelBuild()
        {
            final var fs = InMemoryFileSystem.ofExists(
                Path.of("mandrel", "README-Mandrel.md")
            );
            Asserts.steps(
                GraalBuild.Graal.build(cli(Args.mandrelTree()), fs.exec(), fs.install(), Args.roots())
                , Expect.gitClone("graalvm/mandrel-packaging")
                , Expect.mavenDownload()
                , Expect.mavenExtract()
                , Expect.graalMandrelBuild()
            );
        }

        @Test
        void skipBuild()
        {
            final var fs = InMemoryFileSystem.ofExists(Expect.graalOracleBuild().step);
            Asserts.steps(
                GraalBuild.Graal.build(cli(), fs.exec(), fs.install(), Args.roots())
                , Expect.graalOracleBuild().untouched()
            );
        }

        @Test
        void graalOracleLink()
        {
            final var linked = GraalBuild.Graal.link(cli(), Link::new, path -> true);
            assertThat(linked.link(), is(Homes.graal()));
            assertThat(linked.target(), is(Path.of("graal", "sdk", "latest_graalvm_home")));
        }

        @Test
        void graalMandrelLink()
        {
            final var linked = GraalBuild.Graal.link(cli(Args.mandrelTree()), Link::new, path -> true);
            assertThat(linked.link(), is(Expect.graalHome()));
            assertThat(linked.target(), is(Expect.graalHome()));
        }

        @Test
        void labsJDKLink()
        {
            final var options = cli();
            final var linked = GraalBuild.Java.link(options, Link::new);
            assertThat(linked.link(), is(Homes.java()));
            assertThat(linked.target(), is(Path.of("labs-openjdk-11", "java_home")));
        }

        @Test
        void openJDKLink()
        {
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var linked = GraalBuild.Java.link(options, Link::new);
            assertThat(linked.link(), is(Homes.java()));
            assertThat(linked.target(),
                is(Path.of(
                    "jdk11u-dev"
                    , "build"
                    , "graal-server-release"
                    , "images"
                    , "graal-builder-jdk"
                )));
        }

        @Test
        void javaCloneGit()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = GraalBuild.Java.clone(options, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "1"
                , "https://github.com/openjdk/jdk11u-dev"
            );
            os.assertExecutedOneTask(expected,  cloned);
        }

        @Test
        void javaCloneMercurial()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli(
                "--jdk-tree",
                "http://hg.openjdk.java.net/jdk8/jdk8"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = GraalBuild.Java.clone(options, effects);
            final var expected = Steps.Exec.of(
                "hg"
                , "clone"
                , "http://hg.openjdk.java.net/jdk8/jdk8"
            );
            os.assertExecutedOneTask(expected, cloned);
        }

        @Test
        void javaInstall()
        {
            final var fs = InMemoryFileSystem.empty();
            final var tarName = "OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz";
            final var url = format("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%%2B10/%s", tarName);

            Asserts.steps(
                GraalBuild.Java.install(cli(), fs.install())
                , Expect.download(url, String.format("downloads/%s", tarName))
                , Expect.extract(String.format("downloads/%s", tarName), "boot-jdk-11")
            );
        }

        private static GraalBuild.Options cli(String... extra)
        {
            return MiniCli.asOptions("graal-build", GraalBuild.of(List::of), extra);
        }
    }

    @ExtendWith(LoggingExtension.class)
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
            final var command = GraalGet.of(List::of);
            final var result = MiniCli.asError("graal-get", command);
            assertThat(result, is(2));
        }

        private static GraalGet.Options cli(String... extra)
        {
            final var command = GraalGet.of(List::of);
            return MiniCli.asOptions("graal-get", command, extra);
        }
    }

    @ExtendWith(LoggingExtension.class)
    static class CheckMavenBuild
    {
        @Test
        void quarkus()
        {
            final var fs = InMemoryFileSystem.empty();
            final var tree = "https://github.com/quarkusio/quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("-t", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("quarkus")
            );
        }

        @Test
        void skipQuarkus()
        {
            final var fs = InMemoryFileSystem.ofExists(Expect.mavenBuild("quarkus").step);
            final var tree = "https://github.com/quarkusio/quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("-t", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("quarkus").untouched()
            );
        }

        @Test
        void camelQuarkus()
        {
            final var fs = InMemoryFileSystem.empty();
            final var tree = "https://github.com/apache/camel-quarkus/tree/master";
            Asserts.step(
                MavenBuild.build(cli("-t", tree), fs.exec(), Args.roots())
                , Expect.mavenBuild("camel-quarkus", "-Dquarkus.version=999-SNAPSHOT")
            );
        }

        @Test
        void camel()
        {
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel/tree/master"
                , "-aba"
                , "-Pfastinstall,-Pdoesnotexist"
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

        // TODO avoid dup
        private static MavenBuild.Options cli(String... extra)
        {
            final var command = MavenBuild.of(List::of);
            return MiniCli.asOptions("maven-build", command, extra);
        }
    }

    static class CheckMavenTest
    {
        @Test
        void cliAdditionalTestArgsOptions()
        {
            assertThat(
                cli(
                    "-s", "ignore"
                    , "-ata", "b,:c,-y,-Dx=w"
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
                , "--additional-test-args", "p1,:p2,:p3,-p4"
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
            final var options = cli("-s", "quarkus");
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
            final var options = cli("-s", "quarkus-platform");
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
            final var command = MavenTest.of(List::of);
            return MiniCli.asOptions("maven-test", command, extra);
        }
    }

    private static final class MiniCli
    {
        private static <T> T asOptions(String commandName, Object command, String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add(commandName);
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var cli = Cli.of(command);
            final var result = cli.execute(args);
            assertThat(result.exitCode(), is(0));
            // TODO create an umbrella Input interface for all options
            return (T) result.outputs().get(0);
        }

        private static int asError(String commandName, Object command, String...extra)
        {
            final List<String> list = new ArrayList<>();
            list.add(commandName);
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var picoCli = Cli.of(command).picoCli;
            // Force an error and make it silent
            return picoCli
                .setParameterExceptionHandler(
                    (ex, cmdArgs) ->
                        ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput()
                )
                .execute(args);
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
                new Steps.Download.Effects(this::exists, this::touch, d -> {}, () -> OperatingSystem.Type.MACOSX)
                , new Steps.Extract.Effects(exec(), p -> {})
            );
        }

        static InMemoryFileSystem empty()
        {
            return new InMemoryFileSystem(Collections.emptyMap());
        }

        static InMemoryFileSystem ofExists(Path... paths)
        {
            final var map = Stream.of(paths)
                .collect(
                    Collectors.toMap(Function.identity(), marker -> true)
                );
            return new InMemoryFileSystem(map);
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
        private final OperatingSystem.Type type;

        private RecordingOperatingSystem(OperatingSystem.Type type)
        {
            this.type = type;
        }

        static RecordingOperatingSystem macOS()
        {
            return new RecordingOperatingSystem(OperatingSystem.Type.MACOSX);
        }

        public OperatingSystem.Type type()
        {
            return type;
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
            assertThat(actual.info(), is(expected.step.toString()));
            assertThat(actual.exists(), is(true));
            assertThat(actual.touched(), is(expected.touched));
            assertThat(
                actual.path().toString()
                , is(format("%s.marker", Hashing.sha1(expected.step.toString())))
            );
        }

        static void steps(List<Marker> markers, Expect... expects)
        {
            assertThat(markers.toString(), markers.size(), is(expects.length));
            for (int i = 0; i < expects.length; i++)
            {
                step(markers.get(i), expects[i]);
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

        static Expect gitClone(String repo)
        {
            return Expect.of(Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "1"
                , String.format("https://github.com/%s", repo)
            ));
        }

        static Expect javaOpenJDKConfigure()
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
                , "--with-boot-jdk=/home/boot-jdk-11/Contents/Home"
            ));
        }

        static Expect javaOpenJDKMake()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "make"
                , "graal-builder-image"
            ));
        }

        static Expect graalMandrelBuild()
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

        static Expect graalOracleBuild()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("graal", "substratevm")
                , "/today/mx/mx"
                , "--java-home"
                , "/today/java_home"
                , "build"
            ));
        }

        public static Path graalHome()
        {
            return Path.of("graalvm_home");
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

        static Expect guNativeImage()
        {
            return Expect.of(Steps.Exec.of(
                Path.of("graal/bin")
                , "./gu"
                , "install"
                , "native-image"
            ));
        }
    }

    static final class Args
    {
        static String[] mandrelTree()
        {
            return new String[]{
                "--graal-tree"
                , "https://github.com/graalvm/mandrel/tree/master"
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
        QuarkusCheck.check();
    }
}

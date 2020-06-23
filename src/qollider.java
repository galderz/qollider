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

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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
        final var graalBuild = GraalBuild.ofSystem(today, home);
        final var graalGet = GraalGet.ofSystem(today);
        final var mavenBuild = MavenBuild.ofSystem(today, home);
        final var mavenTest = MavenTest.ofSystem(today);

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
        return new GraalGet(new GraalGet.RunGet(fs));
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

    final static class RunGet implements Function<Options, List<?>>
    {
        final FileSystem fs;

        RunGet(FileSystem fs)
        {
            this.fs = fs;
        }

        @Override
        public List<?> apply(Options options)
        {
            fs.mkdirs(options.graal);

            final var os = OperatingSystem.of(fs);
            final var web = Web.of(fs);

            final var exec = new Steps.Exec.Effects(fs::exists, os::exec, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, web::download, fs::touch, os::type);

            return List.of(
                Graal.get(options, exec, download)
                , Graal.link(options, fs::symlink)
            );
        }
    }

    static final class Graal
    {
        static List<Marker> get(
            Options options
            , Steps.Exec.Effects exec
            , Steps.Download.Effects download
        )
        {
            final var install = new Steps.Install(options.url, options.graal);
            final var installMarkers = Steps.Install.install(install, download, exec);

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
        defaultValue = "https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.1-b02"
        , description = "JDK source tree URL"
        , names =
        {
            "-jt"
            , "--jdk-tree"
        }
    )
    URI jdkTree;

    @Option(
        defaultValue = "https://github.com/graalvm/mx/tree/master"
        , description = "mx source tree URL"
        , names =
        {
            "-mt"
            , "--mx-tree"
        }
    )
    URI mxTree;

    @Option(
        defaultValue = "https://github.com/oracle/graal/tree/master"
        , description = "Graal source tree URL"
        , names =
        {
            "-gt"
            , "--graal-tree"
        }
    )
    URI graalTree;

    private GraalBuild(Function<Options, List<?>> runner)
    {
        this.runner = runner;
    }

    static GraalBuild ofSystem(FileSystem today, FileSystem home)
    {
        return new GraalBuild(new GraalBuild.RunBuild(today, home));
    }

    static GraalBuild of(Function<Options, List<?>> runner)
    {
        return new GraalBuild(runner);
    }

    @Override
    public List<?> call()
    {
        final var options = Options.of(
            jdkTree
            , mxTree
            , graalTree
        );
        return runner.apply(options);
    }

    final static class RunBuild implements Function<Options, List<?>>
    {
        final FileSystem today;
        final FileSystem home;

        RunBuild(FileSystem today, FileSystem home)
        {
            this.today = today;
            this.home = home;
        }

        @Override
        public List<?> apply(Options options)
        {
            home.mkdirs(options.bootJdkPath());

            final var osToday = OperatingSystem.of(today);
            final var osHome = OperatingSystem.of(home);

            // TODO unroll git clones
            final var repos = Options.repositories(options);
            return List.of(
                Java.clone(options, Steps.Exec.Effects.of(osToday))
                , Git.clone(repos, Steps.Exec.Effects.of(osToday)).toArray()
                , Java.install(options
                    , Steps.Exec.Effects.of(osHome)
                    , Steps.Download.Effects.of(Web.of(home), osHome)
                )
                , Java.build(options, Steps.Exec.Effects.of(osToday), osToday::type)
                , Java.link(options, today::symlink)
                , Graal.build(options, Steps.Exec.Effects.of(osToday))
                , Graal.link(options, today::symlink)
            );
        }
    }

    record Options(
        Repository jdk
        , Repository mx
        , Repository graal
    )
    {
        Java.Type javaType()
        {
            return Java.type(jdk);
        }

        Path mxPath()
        {
            return Path.of(mx.name(), "mx");
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

        // TODO take advantage of URI to Repository converter (remove factory)
        static Options of(
            URI jdk
            , URI mx
            , URI graal
        )
        {
            return new Options(
                Repository.of(jdk)
                , Repository.of(mx)
                , Repository.of(graal)
            );
        }

        static List<Repository> repositories(Options options)
        {
            final var urls = new ArrayList<Repository>();
            urls.add(options.mx);
            urls.add(options.graal);
            return urls;
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
        )
        {
            final var home = Path.of("..", "..", "..");
            final var bootJdkHome = home.resolve(options.bootJdkHome(osType));
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

            final var link = Homes.graalJava();
            return symLink.apply(link, target);
        }

        // TODO create a record to capture Java version info (major, minor, micro, build)
        static List<Marker> install(
            Options options
            , Steps.Exec.Effects exec
            , Steps.Download.Effects download
        )
        {
            final var javaVersionMajor = "11";
            final var javaVersionBuild = "10";
            final var javaVersion = String.format("%s.0.7", javaVersionMajor);
            final var javaBaseUrl = String.format(
                "https://github.com/AdoptOpenJDK/openjdk%s-binaries/releases/download"
                , javaVersionMajor
            );

            final var osType = download.osType().get();
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

            return Steps.Install.install(
                new Steps.Install(url, options.bootJdkPath())
                , download
                , exec
            );
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
                    , "--with-extra-cflags='-fcommon'"
                    , "--enable-aot=no"
                    , String.format("--with-boot-jdk=%s", bootJdkHome.toString())
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
            return switch (repo.organization())
                {
                    case "openjdk" -> Type.OPENJDK;
                    case "graalvm" -> Type.LABSJDK;
                    default -> throw new IllegalStateException(
                        "Unexpected value: " + repo.name()
                    );
                };
        }

        enum Type
        {
            OPENJDK, LABSJDK
        }
    }

    static final class Graal
    {
        static Marker build(Options options, Steps.Exec.Effects effects)
        {
            final var root = Path.of("..", "..");
            return Steps.Exec.run(
                Steps.Exec.of(
                    Graal.svm(options)
                    , root.resolve(options.mxPath()).toString()
                    , "--java-home"
                    , root.resolve(Homes.graalJava()).toString()
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

        static Path svm(Options options)
        {
            return Path.of(options.graal.name(), "substratevm");
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

    static MavenBuild ofSystem(FileSystem today, FileSystem home)
    {
        return new MavenBuild(new BuildRunner(today, home));
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

    record BuildRunner(FileSystem today, FileSystem home) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
        {
            home.mkdirs(Path.of("maven"));

            final var osToday = OperatingSystem.of(today);
            final var osHome = OperatingSystem.of(home);

            return List.of(
                Git.clone(options.tree, Steps.Exec.Effects.of(osToday))
                , Maven.install(
                    Steps.Exec.Effects.of(osHome)
                    , Steps.Download.Effects.of(Web.of(home), osHome)
                )
                , MavenBuild.build(options, Steps.Exec.Effects.of(osToday))
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

    static Marker build(Options options, Steps.Exec.Effects effects)
    {
        final var project = options.project();
        final var arguments =
            arguments(options, project.toString())
                .toArray(String[]::new);

        return Steps.Exec.run(
            Steps.Exec.of(project, arguments)
            , effects
        );
    }

    private static Stream<String> arguments(Options options, String directory)
    {
        final var home = Path.of("..", "..", "..");
        final var mvn = Path.of("maven", "bin", "mvn");
        // TODO would adding -Dmaven.test.skip=true work? it skips compiling tests...
        final var arguments = Stream.concat(
            Stream.of(
                home.resolve(mvn).toString()
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

    static MavenTest ofSystem(FileSystem fs)
    {
        return new MavenTest(new TestRunner(fs));
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

    record TestRunner(FileSystem today) implements Function<Options, List<?>>
    {
        @Override
        public List<?> apply(Options options)
        {
            final var os = OperatingSystem.of(today);
            Maven.test(options, os::exec);
            return List.of();
        }
    }

    record Options(String suite, List<String> additionalTestArgs) {}

    static class Maven
    {
        static void test(Options options, Consumer<Steps.Exec> exec)
        {
            final var task = Maven.toTest(options);
            Maven.doTest(task, exec);
        }

        private static void doTest(
            Steps.Exec task
            , Consumer<Steps.Exec> exec
        )
        {
            exec.accept(task);
        }

        private static Steps.Exec toTest(Options options)
        {
            final var directory = Maven.suitePath(options.suite);
            final var arguments = arguments(options, directory).toArray(String[]::new);
            return Steps.Exec.of(
                directory
                , List.of(Homes.EnvVars.graal())
                , arguments
            );
        }

        private static Stream<String> arguments(Options options, Path directory)
        {
            final var home = Path.of("..", "..", "..");
            final var mvn = Path.of("maven", "bin", "mvn");

            final var args = Stream.of(
                home.resolve(mvn).toString()
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
}

final class Maven
{
    static List<Marker> install(Steps.Exec.Effects exec, Steps.Download.Effects download)
    {
        final var version = "3.6.3";
        final var url = URLs.of(
            "https://downloads.apache.org/maven/maven-3/%1$s/binaries/apache-maven-%1$s-bin.tar.gz"
            , version
        );

        return Steps.Install.install(
            new Steps.Install(url, Path.of("maven"))
            , download
            , exec
        );
    }
}

record Repository(
    String organization
    , String name
    , Repository.Type type
    , String branch
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
            final var cloneUri = gitCloneUri(organization, name);
            return new Repository(organization, name, Type.GIT, branch, cloneUri);
        }
        else if (host.equals("hg.openjdk.java.net"))
        {
            final var organization = "openjdk";
            final var name = path.getFileName().toString();
            return new Repository(organization, name, Type.MERCURIAL, null, uri);
        }
        throw new IllegalStateException("Unknown repository type");
    }

    static List<Repository> of(List<String> uris)
    {
        return uris.stream()
            .map(Repository::of)
            .collect(Collectors.toList());
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

record EnvVar(
    String name
    , Function<Path, Path> value
) {}

interface Step {}

// TODO remove steps and just use Step (eventually sealed types in Java 15)
final class Steps
{
    // Install = Download + Extract
    record Install(URL url, Path path) implements Step
    {
        static List<Marker> install(
            Install install
            , Download.Effects download
            , Exec.Effects exec
        )
        {
            final var url = install.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var tarPath = directory.resolve(fileName);

            final var downloadMarker = Download.lazy(
                new Download(url, tarPath)
                , download
            );

            final var extractMarker = Exec.run(
                Exec.of(
                    "tar"
                    , "-xzvpf"
                    , tarPath.toString()
                    , "-C"
                    , install.path.toString()
                    , "--strip-components"
                    , "1"
                )
                , exec
            );

            return List.of(downloadMarker, extractMarker);
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
            Predicate<Marker> exists
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
            Predicate<Marker> exists
            , Consumer<Download> download
            , Function<Marker, Boolean> touch
            , Supplier<OperatingSystem.Type> osType
        )
        {
            static Effects of(Web web, OperatingSystem os)
            {
                return new Effects(os.fs::exists, web::download, os.fs::touch, os::type);
            }
        }
    }
}

class Git
{
    static Marker clone(Repository repo, Steps.Exec.Effects effects)
    {
        return Steps.Exec.run(
            Steps.Exec.of(toClone(repo))
            , effects
        );
    }

    static List<Marker> clone(List<Repository> repos, Steps.Exec.Effects effects)
    {
        return repos.stream()
            .map(repo -> clone(repo, effects))
            .collect(Collectors.toList());
    }

    static String[] toClone(Repository repo)
    {
        return new String[]{
            "git"
            , "clone"
            , "-b"
            , repo.branch()
            , "--depth"
            , "10"
            , repo.cloneUri().toString()
        };
    }
}

class Homes
{
    static Path graalJava()
    {
        return Path.of("graalvm_java_home");
    }

    static Path graal()
    {
        return Path.of("graalvm_home");
    }

    static class EnvVars
    {
        static EnvVar graal()
        {
            return new EnvVar(
                "JAVA_HOME"
                , root -> root.resolve(Homes.graal())
            );
        }
    }
}

// Boundary value
record Marker(boolean exists, boolean touched, Path path, String info)
{
    Marker query(Predicate<Marker> existsFn)
    {
        final var exists = existsFn.test(this);
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
        final var path = Path.of(String.format("%s.marker", hash));
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
            throw new RuntimeException(String.format(
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

    boolean exists(Marker marker)
    {
        return root.resolve(marker.path()).toFile().exists();
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
                throw new RuntimeException(String.format(
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
    static final Logger LOG = LogManager.getLogger(FileSystem.class);

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
        LOG.debug("Execute {} in {}", taskList, directory);
        try
        {
            var processBuilder = new ProcessBuilder(taskList)
                .directory(directory.toFile())
                .inheritIO();

            exec.envVars().forEach(
                envVar -> processBuilder.environment()
                    .put(envVar.name(), envVar.value().apply(fs.root).toString())
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
        return String.format("%x", new BigInteger(1, SHA1.digest()));
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
    static final Logger LOG = LogManager.getLogger(Web.class);

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
            return String.format("%.1f %ciB", value / 1024.0, ci.current());
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
        return URLs.of(String.format(format, args));
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
        listener.getSummary().printTo(new PrintWriter(System.out));
        listener.getSummary().printFailuresTo(new PrintWriter(System.err));
        final var failureCount = listener.getSummary().getTestsFailedCount();
        if (failureCount > 0)
        {
            throw new AssertionError(String.format(
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
            assertThat(repo.cloneUri(), is(URI.create("https://github.com/openjdk/jdk11u-dev")));
        }

        @Test
        void gitCloneEmpty()
        {
            final var os = RecordingOperatingSystem.macOS();
            final List<Repository> repos = emptyList();
            final var effects = new Steps.Exec.Effects(m -> false, os::record, m -> false);
            final var cloned = Git.clone(repos, effects);
            os.assertNumberOfTasks(0);
            assertThat(cloned.size(), is(0));
        }

        @Test
        void gitCloneSelective()
        {
            final var args = new String[]{
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "10"
                , "https://github.com/openjdk/jdk11u-dev"
            };
            final var fs = InMemoryFileSystem.ofExists(Steps.Exec.of(args));
            final var os = RecordingOperatingSystem.macOS();
            final List<Repository> repos = Repository.of(
                List.of(
                    "https://github.com/openjdk/jdk11u-dev/tree/master"
                    , "https://github.com/apache/camel-quarkus/tree/quarkus-master"
                )
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = Git.clone(repos, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "quarkus-master"
                , "--depth"
                , "10"
                , "https://github.com/apache/camel-quarkus"
            );
            os.assertExecutedOneTask(expected, cloned.get(1));
        }

        @Test
        void gitCloneSingle()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = RecordingOperatingSystem.macOS();
            final List<Repository> repos = Repository.of(
                List.of(
                    "https://github.com/openjdk/jdk11u-dev/tree/master"
                )
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = Git.clone(repos, effects);
            final var expected = Steps.Exec.of(
                "git"
                , "clone"
                , "-b"
                , "master"
                , "--depth"
                , "10"
                , "https://github.com/openjdk/jdk11u-dev"
            );
            os.assertExecutedOneTask(expected, cloned.get(0));
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
            final var markers = GraalBuild.Java.build(options, effects, os::type);
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
            final var markers = GraalBuild.Java.build(options, effects, os::type);
            assertThat(markers.get(0).touched(), is(true));
            assertThat(markers.get(1).touched(), is(true));
            os.assertNumberOfTasks(2);
            final var configure = Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                , "--with-extra-cflags='-fcommon'"
                , "--enable-aot=no"
                , "--with-boot-jdk=../../../boot-jdk-11/Contents/Home"
            );
            os.assertExecutedTask(configure, markers.get(0));
            os.forward();
            final var make = Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "make"
                , "graal-builder-image"
            );
            os.assertExecutedTask(make, markers.get(1));
        }

        @Test
        void skipJavaOpenJDK()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var configure = Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                , "--with-extra-cflags='-fcommon'"
                , "--enable-aot=no"
                , "--with-boot-jdk=../../../boot-jdk-11/Contents/Home"
            );
            final var make = Steps.Exec.of(
                Path.of("jdk11u-dev")
                , "make"
                , "graal-builder-image"
            );
            final var fs = InMemoryFileSystem.ofExists(configure, make);
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type);
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
                , "../../../boot-jdk-11/Contents/Home"
            );
            final var fs = InMemoryFileSystem.ofExists(configure);
            final var options = cli();

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, effects, os::type);
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
            );
            assertThat(linked.link(), is(Homes.graal()));
        }

        @Test
        void graalBuild()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli();
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = GraalBuild.Graal.build(options, effects);

            final var expected = Steps.Exec.of(
                Path.of("graal", "substratevm")
                , "../../mx/mx"
                , "--java-home"
                , "../../graalvm_java_home"
                , "build"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void skipGraalBuild()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var step = Steps.Exec.of(
                Path.of("graal", "substratevm")
                , "../../mx/mx"
                , "--java-home"
                , "../../graalvm_java_home"
                , "build"
            );
            final var fs = InMemoryFileSystem.ofExists(step);
            final var options = cli();
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = GraalBuild.Graal.build(options, effects);
            os.assertNumberOfTasks(0);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
        }

        @Test
        void labsJDKLink()
        {
            final var options = cli();
            final var linked = GraalBuild.Java.link(
                options
                , Link::new
            );
            assertThat(linked.link(), is(Homes.graalJava()));
            assertThat(linked.target(), is(Path.of("labs-openjdk-11", "java_home")));
        }

        @Test
        void openJDKLink()
        {
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var linked = GraalBuild.Java.link(
                options
                , Link::new
            );
            assertThat(linked.link(), is(Homes.graalJava()));
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
                , "10"
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
            final var os = RecordingOperatingSystem.macOS();
            final var web = new RecordingWeb();
            final var options = cli();
            final var exec = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, web::record, fs::touch, os::type);
            final var markers = GraalBuild.Java.install(options, exec, download);
            final var downloadTask = web.tasks.remove();
            final var tarName = "OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz";
            final var url = URLs.of("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%%2B10/%s", tarName);
            assertThat(downloadTask.url(), is(url));
            assertThat(downloadTask.path(), is(Path.of("downloads", tarName)));
            assertThat(markers.get(0).touched(), is(true));
            final var expected = Steps.Exec.of(
                "tar",
                "-xzvpf"
                , String.format("downloads/%s", tarName)
                , "-C"
                , "boot-jdk-11"
                , "--strip-components"
                , "1"
            );
            os.assertExecutedOneTask(expected, markers.get(1));
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
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--url",
                "https://doestnotexist.com/archive.tar.gz"
            );
            final var exec = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, RecordingWeb::noop, fs::touch, os::type);
            final var markers = GraalGet.Graal.get(options, exec, download);
            assertThat(markers.size(), is(2));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(true));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(t ->
                assertThat(
                    String.join(" ", t.args())
                    , is(equalTo("tar -xzvpf downloads/archive.tar.gz -C graalvm/graal --strip-components 1"))
                )
            );
        }

        @Test
        void getAndDownloadNativeImage()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--url",
                "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz"
            );
            final var exec = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, RecordingWeb::noop, fs::touch, os::type);
            final var markers = GraalGet.Graal.get(options, exec, download);
            assertThat(markers.size(), is(3));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(true));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(true));
            final var downloadNativeImageMarker = markers.get(2);
            assertThat(downloadNativeImageMarker.exists(), is(true));
            assertThat(downloadNativeImageMarker.touched(), is(true));
        }

        @Test
        void skipBothDownloadAndExtract()
        {
            final var url = "https://skip.both/download";
            final var downloadPath = Path.of("downloads", "download");

            final var extractArgs = new String[]{
                "tar"
                , "-xzvpf"
                , downloadPath.toString()
                , "-C"
                , Path.of("graalvm", "graal").toString()
                , "--strip-components"
                , "1"
            };

            final var options = cli("--url", url);

            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.ofExists(
                new Steps.Download(URLs.of(url), downloadPath)
                , Steps.Exec.of(extractArgs)
            );
            final var exec = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, RecordingWeb::noop, fs::touch, os::type);
            final var markers = GraalGet.Graal.get(options, exec, download);
            os.assertNumberOfTasks(0);
            assertThat(markers.size(), is(2));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(false));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(false));
        }

        @Test
        void skipOnlyDownload()
        {
            final var url = "https://skip.only/download";
            final var path = Path.of("downloads", "download");
            final var step = new Steps.Download(URLs.of(url), path);
            final var options = cli("--url", url);

            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.ofExists(step);

            final var exec = new Steps.Exec.Effects(fs::exists, os::record, fs::touch);
            final var download = new Steps.Download.Effects(fs::exists, RecordingWeb::noop, fs::touch, os::type);
            final var markers = GraalGet.Graal.get(options, exec, download);
            os.assertNumberOfTasks(1);
            assertThat(markers.size(), is(2));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(false));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(true));
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
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.build(options, effects);
            final var expected = Steps.Exec.of(
                Path.of("quarkus")
                , "../../../maven/bin/mvn"
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void skipQuarkus()
        {
            final var args = new String[]{
                "../../../maven/bin/mvn"
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
            };
            final var os = RecordingOperatingSystem.macOS();
            final var path = Path.of("quarkus");
            final var fs = InMemoryFileSystem.ofExists(Steps.Exec.of(path, args));
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.build(options, effects);
            os.assertNumberOfTasks(0);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
        }

        @Test
        void camelQuarkus()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel-quarkus/tree/master"
            );

            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.build(options, effects);
            final var expected = Steps.Exec.of(
                Path.of("camel-quarkus")
                , "../../../maven/bin/mvn"
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
                , "-Dquarkus.version=999-SNAPSHOT"
            );
            os.assertExecutedOneTask(expected, marker);
        }

        @Test
        void camel()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel/tree/master"
                , "-aba"
                , "-Pfastinstall,-Pdoesnotexist"
            );
            final var effects = new Steps.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.build(options, effects);
            final var expected = Steps.Exec.of(
                Path.of("camel")
                , "../../../maven/bin/mvn"
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
                , "-Pfastinstall"
                , "-Pdoesnotexist"
            );
            os.assertExecutedOneTask(expected, marker);
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
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(t ->
                assertThat(
                    String.join(" ", t.args())
                    , is(equalTo("../../../maven/bin/mvn install -Dnative -Dformat.skip p1 :p2 :p3 -p4"))
                )
            );
        }

        @Test
        void suite()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("--suite", "suite-a");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertAllTasks(t -> assertThat(t.args().stream().findFirst(), is(Optional.of("../../../maven/bin/mvn"))));
            os.assertTask(t -> assertThat(t.directory(), is(Path.of("suite-a"))));
        }

        @Test
        void quarkus()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("-s", "quarkus");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("../../../maven/bin/mvn")));
                assertThat(task.directory(), is(Path.of("quarkus/integration-tests")));
            });
        }

        @Test
        void quarkusPlatform()
        {
            final var os = RecordingOperatingSystem.macOS();
            final var options = cli("-s", "quarkus-platform");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("quarkus-platform")));
                final var arguments = new ArrayList<>(task.args());
                assertThat(arguments.stream().findFirst(), is(Optional.of("../../../maven/bin/mvn")));
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
        final Map<Marker, Boolean> exists;

        private InMemoryFileSystem(Map<Marker, Boolean> exists)
        {
            this.exists = exists;
        }

        boolean exists(Marker marker)
        {
            final var doesExist = exists.get(marker);
            return doesExist == null ? false : doesExist;
        }

        boolean touch(Marker marker)
        {
            return true;
        }

        static InMemoryFileSystem empty()
        {
            return new InMemoryFileSystem(Collections.emptyMap());
        }

        static InMemoryFileSystem ofExists(Step... steps)
        {
            final var map = Stream.of(steps)
                .map(Marker::of)
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
                , is(Path.of(String.format("%s.marker", Hashing.sha1(expected.toString()))))
            );
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
            tasks.remove();
        }

        @SuppressWarnings("unchecked")
        private <T> T peekTask()
        {
            return (T) tasks.peek();
        }
    }

    // TODO merge with RecordingOperatingSystem? Or wait until no need for record?
    private static final class RecordingWeb
    {
        private final Queue<Steps.Download> tasks = new ArrayDeque<>();

        void record(Steps.Download task)
        {
            tasks.offer(task);
        }

        static void noop(Steps.Download task) {}
    }

    public static void main(String[] args)
    {
        QuarkusCheck.check();
    }
}

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
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

        final var fs = FileSystem.ofSystem();
        final var os = new OperatingSystem(fs);
        final var web = new Web(fs);

        final var graalBuild = GraalBuild.ofSystem(fs, os);
        final var graalGet = GraalGet.ofSystem(fs, os, web);
        final var mavenBuild = MavenBuild.ofSystem(fs, os);
        final var quarkusClean = QuarkusClean.ofSystem(fs);
        final var quarkusTest = MavenTest.ofSystem(fs, os);

        final var cli = Cli.of(
            graalBuild
            , graalGet
            , mavenBuild
            , quarkusClean
            , quarkusTest
        );
        final var result = cli.execute(args);
        if (0 == result.exitCode())
        {
            LOG.info(
                "Executed: {}"
                , result.subResult().results().stream()
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

    <T> ExecResult<T> execute(String... args)
    {
        final var exitCode = picoCli.execute(args);
        ParseResult parseResult = picoCli.getParseResult();
        CommandLine sub = parseResult.subcommand().commandSpec().commandLine();
        return new ExecResult<>(exitCode, sub.getExecutionResult());
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

    record ExecResult<T>(int exitCode, SubResult<T> subResult) {}
}

// TODO remove
@Command(
    name = "clean"
    , aliases = {"c"}
    , description = "Clean quarkus."
    , mixinStandardHelpOptions = true
)
class QuarkusClean implements Runnable
{
    static final Logger LOG = LogManager.getLogger(QuarkusClean.class);

    private final Consumer<List<String>> runner;

    @Option(
        description = "Individual projects to clean."
        , names =
        {
            "-p"
            , "--projects"
        }
        , split = ","
    )
    List<String> projects = new ArrayList<>();

    private QuarkusClean(Consumer<List<String>> runner)
    {
        this.runner = runner;
    }

    static QuarkusClean ofSystem(FileSystem fs)
    {
        return new QuarkusClean(new RunClean(fs));
    }

    @Override
    public void run()
    {
        LOG.info("Clean!");
        runner.accept(projects);
    }

    private static final class RunClean implements Consumer<List<String>>
    {
        private final FileSystem fs;

        public RunClean(FileSystem fs)
        {
            this.fs = fs;
        }

        @Override
        public void accept(List<String> projects)
        {
            clean(projects, fs::deleteRecursive);
        }
    }

    static void clean(List<String> projects, Consumer<Path> delete)
    {
        if (projects.isEmpty())
        {
            delete.accept(Path.of(""));
        }
        else
        {
            projects.stream()
                .map(Path::of)
                .forEach(delete);
        }
    }
}

@Command(
    name = "graal-get"
    , aliases = {"gg"}
    , description = "Get Graal."
    , mixinStandardHelpOptions = true
)
class GraalGet implements Callable<SubResult<GraalGet.Options>>
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

    static GraalGet ofSystem(FileSystem fs, OperatingSystem os, Web web)
    {
        return new GraalGet(new GraalGet.RunGet(fs, os, web));
    }

    static GraalGet of(Function<Options, List<?>> runner)
    {
        return new GraalGet(runner);
    }

    @Override
    public SubResult<GraalGet.Options> call()
    {
        final var options = new Options(url, Path.of("graalvm", "graal"));
        return new SubResult<>(options, runner.apply(options));
    }

    final static class RunGet implements Function<Options, List<?>>
    {
        final FileSystem fs;
        final OperatingSystem os;
        final Web web;

        RunGet(FileSystem fs, OperatingSystem os, Web web)
        {
            this.fs = fs;
            this.os = os;
            this.web = web;
        }

        @Override
        public List<?> apply(Options options)
        {
            fs.mkdirs(options.graal);
            return List.of(
                Graal.get(options, fs::exists, web::download, os::exec, fs::touch)
                , Graal.link(options, fs::symlink)
            );
        }
    }

    static final class Graal
    {
        static List<Marker> get(
            Options options
            , Predicate<Marker> exists
            , BiConsumer<URL, Path> download
            , Consumer<Tasks.Exec> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var url = options.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var tarPath = directory.resolve(fileName);

            final var downloadMarker = Tasks.Download.lazy(
                new Tasks.Download(options.url, tarPath)
                , new Tasks.Download.Effects(exists, download, touch)
            );

            final var extractMarker = Tasks.Exec.run(
                Tasks.Exec.of(
                    "tar"
                    , "-xzvpf"
                    , tarPath.toString()
                    , "-C"
                    , options.graal.toString()
                    , "--strip-components"
                    , "1"
                )
                , new Tasks.Exec.Effects(exists, exec, touch)
            );

            final var orgName = Path.of(options.url.getPath()).getName(0);
            if (!orgName.equals(Path.of("graalvm")))
                return Arrays.asList(downloadMarker, extractMarker);

            final var nativeImageMarker = Tasks.Exec.run(
                Tasks.Exec.of(
                    List.of(
                        "./gu"
                        , "install"
                        , "native-image"
                    )
                    , Path.of("graal", "bin")
                )
                , new Tasks.Exec.Effects(exists, exec, touch)
            );

            return Arrays.asList(downloadMarker, extractMarker, nativeImageMarker);
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
class GraalBuild implements Callable<SubResult<GraalBuild.Options>>
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

    static GraalBuild ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new GraalBuild(new GraalBuild.RunBuild(fs, os));
    }

    static GraalBuild of(Function<Options, List<?>> runner)
    {
        return new GraalBuild(runner);
    }

    @Override
    public SubResult<Options> call()
    {
        final var options = Options.of(
            jdkTree
            , mxTree
            , graalTree
            , Path.of("graalvm")
        );
        return new SubResult<>(options, runner.apply(options));
    }

    final static class RunBuild implements Function<Options, List<?>>
    {
        final FileSystem fs;
        final OperatingSystem os;

        RunBuild(FileSystem fs, OperatingSystem os)
        {
            this.fs = fs;
            this.os = os;
        }

        @Override
        public List<?> apply(Options options)
        {
            final var parent = fs.mkdirs(options.parent);

            final var effects = new Tasks.Exec.Effects(fs::exists, os::exec, fs::touch);

            final var repos = Options.repositories(options);
            return List.of(
                Java.clone(options, effects)
                , Git.clone(repos, parent, effects).toArray()

                // TODO boot jdk should be installed in java
                , Java.build(options, os::bootJdkHome, effects)
                , Java.link(options, fs::symlink)

                , Graal.build(options, effects)
                , Graal.link(options, fs::symlink)
            );
        }
    }

    record Options(
        Repository jdk
        , Repository mx
        , Repository graal
        , Path parent
    )
    {
        Java.Type javaType()
        {
            return Java.type(jdk);
        }

        Path jdkPath()
        {
            return parent.resolve(jdk.name());
        }

        Path graalPath()
        {
            return parent.resolve(graal.name());
        }

        Path mxPath()
        {
            return Path.of(mx.name(), "mx");
        }

        // TODO take advantage of URI to Repository converter (remove factory)
        static Options of(
            URI jdk
            , URI mx
            , URI graal
            , Path parent
        )
        {
            return new Options(
                Repository.of(jdk)
                , Repository.of(mx)
                , Repository.of(graal)
                , parent
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
        static Marker clone(Options options, Tasks.Exec.Effects effects)
        {
            return switch (options.jdk.type())
            {
                case GIT ->
                    Git.clone(
                        options.jdk
                        , options.parent
                        , effects
                    );
                case MERCURIAL ->
                    Mercurial.clone(options.jdk, options.parent, effects);
            };
        }

        static List<Marker> build(
            Options options
            , Supplier<Path> bootJdkHome
            , Tasks.Exec.Effects effects
        )
        {
            final var jdkPath = options.jdkPath();
            final var tasks = switch (options.javaType())
                {
                    case OPENJDK -> Java.OpenJDK.buildSteps(jdkPath, bootJdkHome);
                    case LABSJDK -> Java.LabsJDK.buildSteps(jdkPath, bootJdkHome);
                };

            return tasks
                .map(t -> Tasks.Exec.run(t, effects))
                .collect(Collectors.toList());
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var jdkPath = options.jdkPath();
            final var target =
                switch (options.javaType())
                    {
                        case OPENJDK -> Java.OpenJDK.javaHome(jdkPath);
                        case LABSJDK -> Java.LabsJDK.javaHome(jdkPath);
                    };

            final var link = Homes.graalJava();
            return symLink.apply(link, target);
        }

        private static final class OpenJDK
        {
            static Stream<Tasks.Exec> buildSteps(
                Path jdkPath
                , Supplier<Path> bootJdkHome
            )
            {
                return Stream.of(
                    configureSh(jdkPath, bootJdkHome)
                    , makeGraalJDK(jdkPath)
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

            private static Tasks.Exec configureSh(Path jdk, Supplier<Path> bootJdkHome)
            {
                return Tasks.Exec.of(
                    List.of(
                        "sh"
                        , "configure"
                        , "--with-conf-name=graal-server-release"
                        , "--disable-warnings-as-errors"
                        , "--with-jvm-features=graal"
                        , "--with-jvm-variants=server"
                        , "--enable-aot=no"
                        , String.format("--with-boot-jdk=%s", bootJdkHome.get())
                    )
                    , jdk
                );
            }

            private static Tasks.Exec makeGraalJDK(Path jdk)
            {
                return Tasks.Exec.of(
                    List.of(
                        "make"
                        , "graal-builder-image"
                    )
                    , jdk
                );
            }
        }

        private static final class LabsJDK
        {
            static Stream<Tasks.Exec> buildSteps(Path jdk, Supplier<Path> bootJdkPath)
            {
                return Stream.of(buildJDK(jdk, bootJdkPath));
            }

            static Path javaHome(Path jdk)
            {
                return jdk.resolve("java_home");
            }

            private static Tasks.Exec buildJDK(Path jdk, Supplier<Path> bootJdkPath)
            {
                // TODO pass boot jdk via --boot-jdk (e.g. python build_labsjdk.py --boot-jdk ${HOME}/.sdkman/candidates/java/11.0.7.hs-adpt)
                return Tasks.Exec.of(
                    List.of(
                        "python"
                        , "build_labsjdk.py"
                    )
                    , jdk
                    , Homes.EnvVars.bootJava(bootJdkPath)
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
        static Marker build(Options options, Tasks.Exec.Effects effects)
        {
            final var graalVmRoot = Path.of("..", "..");
            final var root = graalVmRoot.resolve("..");
            return Tasks.Exec.run(
                Tasks.Exec.of(
                    List.of(
                        graalVmRoot.resolve(options.mxPath()).toString()
                        , "--java-home"
                        , root.resolve(Homes.graalJava()).toString()
                        , "build"
                    )
                    , Graal.svm(options)
                )
                , effects
            );
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var target = options.graalPath().resolve(
                Path.of(
                    "sdk"
                    , "latest_graalvm_home"
                )
            );

            return symLink.apply(Homes.graal(), target);
        }

        static Path svm(Options options)
        {
            return options.graalPath().resolve("substratevm");
        }
    }
}

@Command(
    name = "maven-build"
    , aliases = {"mb"}
    , description = "Maven build."
    , mixinStandardHelpOptions = true
)
class MavenBuild implements Callable<SubResult<MavenBuild.Options>>
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

    static MavenBuild ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new MavenBuild(new MavenBuild.RunBuild(fs, os));
    }

    static MavenBuild of(Function<Options, List<?>> runner)
    {
        return new MavenBuild(runner);
    }

    @Override
    public SubResult<Options> call()
    {
        final var options = new Options(tree, additionalBuildArgs);
        return new SubResult<>(options, runner.apply(options));
    }

    final static class RunBuild implements Function<Options, List<?>>
    {
        final FileSystem fs;
        final OperatingSystem os;

        RunBuild(FileSystem fs, OperatingSystem os)
        {
            this.fs = fs;
            this.os = os;
        }

        @Override
        public List<?> apply(Options options)
        {
            final var parent = Path.of("");
            final var effects = new Tasks.Exec.Effects(fs::exists, os::exec, fs::touch);
            return List.of(
                Git.clone(options.tree, parent, effects)
                , Maven.build(options, effects)
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

    static class Maven
    {
        static Marker build(Options options, Tasks.Exec.Effects effects)
        {
            final var project = options.project();
            final var arguments =
                arguments(options, project.toString())
                    .collect(Collectors.toList());

            return Tasks.Exec.run(
                Tasks.Exec.of(arguments, project)
                , effects
            );
        }

        private static Stream<String> arguments(Options options, String directory)
        {
            // TODO add -DskipITs just in case
            // TODO would adding -Dmaven.test.skip=true work? it skips compiling tests...
            final var arguments = Stream.concat(
                Stream.of(
                    "mvn" // ?
                    , "install"
                    , "-DskipTests"
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
}

@Command(
    name = "maven-test"
    , aliases = {"mt"}
    , description = "Maven test."
    , mixinStandardHelpOptions = true
)
class MavenTest implements Callable<SubResult<MavenTest.Options>>
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

    static MavenTest ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new MavenTest(new RunTest(fs, os));
    }

    static MavenTest of(Function<Options, List<?>> runner)
    {
        return new MavenTest(runner);
    }

    @Override
    public SubResult<Options> call()
    {
        var options = new Options(suite, additionalTestArgs);
        return new SubResult<>(options, runner.apply(options));
    }

    final static class RunTest implements Function<Options, List<?>>
    {
        final FileSystem fs;
        final OperatingSystem os;

        RunTest(FileSystem fs, OperatingSystem os)
        {
            this.fs = fs;
            this.os = os;
        }

        @Override
        public List<?> apply(Options options)
        {
            Maven.test(options, os::exec);
            return List.of();
        }
    }

    record Options(String suite, List<String> additionalTestArgs) {}

    static class Maven
    {
        static void test(Options options, Consumer<Tasks.Exec> exec)
        {
            final var task = Maven.toTest(options);
            Maven.doTest(task, exec);
        }

        private static void doTest(
            Tasks.Exec task
            , Consumer<Tasks.Exec> exec
        )
        {
            exec.accept(task);
        }

        private static Tasks.Exec toTest(Options options)
        {
            final var directory = Maven.suitePath(options.suite);
            final var arguments = arguments(options, directory).collect(Collectors.toList());
            return Tasks.Exec.of(
                arguments
                , directory
                , Homes.EnvVars.graal()
            );
        }

        private static Stream<String> arguments(Options options, Path directory)
        {
            final var args = Stream.of(
                "mvn"
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
    static Marker clone(Repository repository, Path path, Tasks.Exec.Effects effects)
    {
        return Tasks.Exec.run(
            Tasks.Exec.of(
                List.of(
                    "hg"
                    , "clone"
                    , repository.cloneUri().toString()
                )
                , path
            )
            , effects
        );
    }
}

record EnvVar(
    String name
    , Function<Path, Path> value
) {}

final class Tasks
{
    record Exec(
        List<String> args
        , Path directory
        , List<EnvVar> envVars
    )
    {
        static Exec of(List<String> task, Path path, EnvVar... envVars)
        {
            return new Exec(task, path, Arrays.asList(envVars));
        }

        static Exec of(String... task)
        {
            return new Exec(Arrays.asList(task), Path.of(""), emptyList());
        }

        static Marker run(Exec exec, Effects effects)
        {
            final var marker = exec.marker().query(effects.exists);
            if (marker.exists())
                return marker;

            effects.exec().accept(exec);
            return marker.touch(effects.touch);
        }

        Marker marker()
        {
            final var cmd = String.join(" ", args);
            final var hash = Hashing.sha1(cmd);
            final var path = Path.of(String.format("%s.marker", hash));
            return Marker.of(path);
        }

        record Effects(
            Predicate<Marker> exists
            , Consumer<Exec> exec
            , Function<Marker, Boolean> touch
        ) {}
    }

    record Download(URL url, Path path)
    {
        static Marker lazy(Download task, Effects effects)
        {
            final var marker = task.marker().query(effects.exists);
            if (marker.exists())
                return marker;

            effects.download.accept(task.url, task.path);
            return marker.touch(effects.touch);
        }

        // TODO duplicate & make it not rely on internal toString() definitions
        Marker marker()
        {
            final var cmd = this.toString();
            final var hash = Hashing.sha1(cmd);
            final var path = Path.of(String.format("%s.marker", hash));
            return Marker.of(path);
        }

        record Effects(
            Predicate<Marker> exists
            , BiConsumer<URL, Path> download
            , Function<Marker, Boolean> touch
        ) {}
    }
}

class Git
{
    static Marker clone(Repository repo, Path path, Tasks.Exec.Effects effects)
    {
        return Tasks.Exec.run(
            Tasks.Exec.of(toClone(repo), path)
            , effects
        );
    }

    static List<Marker> clone(List<Repository> repos, Path path, Tasks.Exec.Effects effects)
    {
        return repos.stream()
            .map(repo -> clone(repo, path, effects))
            .collect(Collectors.toList());
    }

    static List<String> toClone(Repository repo)
    {
        return List.of(
            "git"
            , "clone"
            , "-b"
            , repo.branch()
            , "--depth"
            , "10"
            , repo.cloneUri().toString()
        );
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

        static EnvVar bootJava(Supplier<Path> path)
        {
            return new EnvVar(
                "JAVA_HOME"
                , ignore -> path.get()
            );
        }
    }
}

// TODO track any exceptional outcome
record SubResult<T>(T options, List<?> results) {}

// Boundary value
record Marker(boolean exists, boolean touched, Path path)
{
    Marker query(Predicate<Marker> existsFn)
    {
        final var exists = existsFn.test(this);
        if (exists)
        {
            return new Marker(true, this.touched, this.path);
        }

        return new Marker(false, this.touched, this.path);
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
            return new Marker(true, true, path);
        }

        return new Marker(false, false, path);
    }

    static Marker of(Path path)
    {
        return new Marker(false, false, path);
    }
}

// Boundary value
record Link(Path link, Path target) {}

// Dependency
class FileSystem
{
    static final Logger LOG = LogManager.getLogger(FileSystem.class);

    final Path root;

    static FileSystem ofSystem()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("ddMM");
        var today = date.format(formatter);
        LOG.info("Today is {}", today);

        var baseDir = Path.of(
            System.getProperty("user.home")
            , "workspace"
            , "qollider"
        );
        LOG.info("Base directory: {}", baseDir);

        final var path = baseDir.resolve(today);
        LOG.info("Root path: {}", path);

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

    Path mkdirs(Path directory)
    {
        return FileSystem.idempotentMkDirs(root.resolve(directory));
    }

    boolean exists(Marker marker)
    {
        return root.resolve(marker.path()).toFile().exists();
    }

    boolean touch(Marker marker)
    {
        long timestamp = System.currentTimeMillis();
        final var path = root.resolve(marker.path());
        final var file = path.toFile();
        if (!file.exists())
        {
            try
            {
                new FileOutputStream(file).close();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        return file.setLastModified(timestamp);
    }

    Link symlink(Path relativeLink, Path relativeTarget)
    {
        final var link = root.resolve(relativeLink);
        final var target = relativeTarget;
        try
        {
            if (Files.exists(link))
                Files.delete(link);

            final var symbolicLink = Files.createSymbolicLink(link, target);
            return new Link(symbolicLink, target);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

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

    OperatingSystem(FileSystem fs)
    {
        this.fs = fs;
    }

    void exec(Tasks.Exec exec)
    {
        final var taskList = exec.args().stream()
            .filter(Predicate.not(String::isEmpty))
            .collect(Collectors.toList());

        final var directory = fs.root.resolve(exec.directory());
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

    Path bootJdkHome()
    {
        return Path.of(System.getenv("BOOT_JDK_HOME"));
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

    Web(FileSystem fs)
    {
        this.fs = fs;
    }

    // TODO add download progress
    void download(URL url, Path file)
    {
        try
        {
            final var urlChannel = new DownloadProgressChannel(
                Channels.newChannel(url.openStream())
            );

            // Create any parent directories as needed
            fs.mkdirs(file.getParent());

            final var path = fs.root.resolve(file);
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
}

final class QuarkusCheck
{
    static void check()
    {
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
            final var os = new RecordingOperatingSystem();
            final List<Repository> repos = emptyList();
            final var effects = new Tasks.Exec.Effects(m -> false, os::record, m -> false);
            final var cloned = Git.clone(repos, Path.of(""), effects);
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
            final var fs = InMemoryFileSystem.ofExists(
                Tasks.Exec.of(args).marker()
            );
            final var os = new RecordingOperatingSystem();
            final List<Repository> repos = Repository.of(
                List.of(
                    "https://github.com/openjdk/jdk11u-dev/tree/master"
                    , "https://github.com/apache/camel-quarkus/tree/quarkus-master"
                )
            );
            final var root = Path.of("root");
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = Git.clone(repos, root, effects);
            final var expectedTask = "git clone -b quarkus-master --depth 10 https://github.com/apache/camel-quarkus";
            os.assertExecutedOneTask(expectedTask, root, cloned.get(1));
        }

        @Test
        void gitCloneSingle()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = new RecordingOperatingSystem();
            final List<Repository> repos = Repository.of(
                List.of(
                    "https://github.com/openjdk/jdk11u-dev/tree/master"
                )
            );
            final var root = Path.of("graalvm");
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = Git.clone(repos, root, effects);
            final var expectedTask = "git clone -b master --depth 10 https://github.com/openjdk/jdk11u-dev";
            os.assertExecutedOneTask(expectedTask, root, cloned.get(0));
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
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli();

            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, os::bootJdkHome, effects);
            final var marker = markers.get(0);
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("python")));
                assertThat(task.directory(), is(Path.of("graalvm", "labs-openjdk-11")));
            });
        }

        @Test
        void javaOpenJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, os::bootJdkHome, effects);
            assertThat(markers.get(0).touched(), is(true));
            assertThat(markers.get(1).touched(), is(true));
            os.assertNumberOfTasks(2);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("sh")));
                assertThat(task.directory(), is(Path.of("graalvm", "jdk11u-dev")));
            });
            os.forward();
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("make")));
                assertThat(task.directory(), is(Path.of("graalvm", "jdk11u-dev")));
            });
        }

        @Test
        void skipJavaOpenJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var configureArgs = new String[]{
                "sh"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                , "--enable-aot=no"
                , "--with-boot-jdk=boot/jdk/home"
            };
            final var markArgs = new String[]{
                "make"
                , "graal-builder-image"
            };
            final var fs = InMemoryFileSystem.ofExists(
                Tasks.Exec.of(configureArgs).marker()
                , Tasks.Exec.of(markArgs).marker()
            );
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var markers = GraalBuild.Java.build(options, os::bootJdkHome, effects);
            os.assertNumberOfTasks(0);
            final var configureMarker = markers.get(0);
            assertThat(configureMarker.exists(), is(true));
            assertThat(configureMarker.touched(), is(false));
            final var makeMarker = markers.get(1);
            assertThat(makeMarker.exists(), is(true));
            assertThat(makeMarker.touched(), is(false));
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
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli();
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = GraalBuild.Graal.build(options, effects);
            final var root = Path.of("graalvm", "graal", "substratevm");
            final var expectedTask = "../../mx/mx --java-home ../../../graalvm_java_home build";
            os.assertExecutedOneTask(expectedTask, root, marker);
        }

        @Test
        void skipGraalBuild()
        {
            final var os = new RecordingOperatingSystem();
            final var args = new String[]{
                "../../mx/mx"
                , "--java-home"
                , "../../../graalvm_java_home"
                , "build"
            };
            final var fs = InMemoryFileSystem.ofExists(
                Tasks.Exec.of(args).marker()
            );
            final var options = cli();
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
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
            assertThat(linked.target(), is(Path.of("graalvm", "labs-openjdk-11", "java_home")));
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
                    "graalvm"
                    ,"jdk11u-dev"
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
            final var os = new RecordingOperatingSystem();
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );
            final var root = Path.of("graalvm");
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = GraalBuild.Java.clone(options, effects);
            final var expectedTask = "git clone -b master --depth 10 https://github.com/openjdk/jdk11u-dev";
            os.assertExecutedOneTask(expectedTask, root, cloned);
        }

        @Test
        void javaCloneMercurial()
        {
            final var fs = InMemoryFileSystem.empty();
            final var os = new RecordingOperatingSystem();
            final var options = cli(
                "--jdk-tree",
                "http://hg.openjdk.java.net/jdk8/jdk8"
            );
            final var root = Path.of("graalvm");
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, fs::touch);
            final var cloned = GraalBuild.Java.clone(options, effects);
            final var expectedTask = "hg clone http://hg.openjdk.java.net/jdk8/jdk8";
            os.assertExecutedOneTask(expectedTask, root, cloned);
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
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--url",
                "https://doestnotexist.com/archive.tar.gz"
            );
            final var markers = GraalGet.Graal.get(
                options
                , fs::exists
                , CheckGraalGet::downloadNoop
                , os::record
                , fs::touch
            );
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
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--url",
                "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz"
            );
            final var markers = GraalGet.Graal.get(
                options
                , fs::exists
                , CheckGraalGet::downloadNoop
                , os::record
                , fs::touch
            );
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

            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(
                new Tasks.Download(URLs.of(url), downloadPath).marker()
                , Tasks.Exec.of(extractArgs).marker()
            );
            final var markers = GraalGet.Graal.get(
                options
                , fs::exists
                , CheckGraalGet::downloadNoop
                , os::record
                , fs::touch
            );
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
            final var marker = new Tasks.Download(URLs.of(url), path).marker();
            final var options = cli("--url", url);

            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(marker);

            final var markers = GraalGet.Graal.get(
                options
                , fs::exists
                , CheckGraalGet::downloadNoop
                , os::record
                , fs::touch
            );
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

        static void downloadNoop(URL url, Path path) {}
    }

    @ExtendWith(LoggingExtension.class)
    static class CheckMavenBuild
    {
        @Test
        void quarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.Maven.build(options, effects);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("quarkus")));
                assertThat(task.args().stream().findFirst(), is(Optional.of("mvn")));
            });
        }

        @Test
        void skipQuarkus()
        {
            final var args = new String[]{
                "mvn"
                , "install"
                , "-DskipTests"
                , "-Dformat.skip"
            };
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(
                Tasks.Exec.of(args).marker()
            );
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.Maven.build(options, effects);
            os.assertNumberOfTasks(0);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
        }

        @Test
        void camelQuarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel-quarkus/tree/master"
            );
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.Maven.build(options, effects);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("camel-quarkus")));
                final var arguments = new ArrayList<>(task.args());
                assertThat(arguments.stream().findFirst(), is(Optional.of("mvn")));
                assertThat(
                    arguments.stream().filter(e -> e.equals("-Dquarkus.version=999-SNAPSHOT")).findFirst()
                    , is(Optional.of("-Dquarkus.version=999-SNAPSHOT"))
                );
            });
        }

        @Test
        void camel()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel/tree/master"
                , "-aba"
                , "-Pfastinstall,-Pdoesnotexist"
            );
            final var effects = new Tasks.Exec.Effects(fs::exists, os::record, m -> true);
            final var marker = MavenBuild.Maven.build(options, effects);
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("camel")));
                final var arguments = new ArrayList<>(task.args());
                assertThat(arguments.stream().findFirst(), is(Optional.of("mvn")));
                assertThat(
                    arguments.stream().filter(e -> e.equals("-Pfastinstall")).findFirst()
                    , is(Optional.of("-Pfastinstall"))
                );
                assertThat(
                    arguments.stream().filter(e -> e.equals("-Pdoesnotexist")).findFirst()
                    , is(Optional.of("-Pdoesnotexist"))
                );
            });
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
            final var os = new RecordingOperatingSystem();
            final var options = cli(
                "--suite", "suite-a"
                , "--additional-test-args", "p1,:p2,:p3,-p4"
            );
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(t ->
                assertThat(
                    String.join(" ", t.args())
                    , is(equalTo("mvn install -Dnative -Dformat.skip p1 :p2 :p3 -p4"))
                )
            );
        }

        @Test
        void suite()
        {
            final var os = new RecordingOperatingSystem();
            final var options = cli("--suite", "suite-a");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertAllTasks(t -> assertThat(t.args().stream().findFirst(), is(Optional.of("mvn"))));
            os.assertTask(t -> assertThat(t.directory(), is(Path.of("suite-a"))));
        }

        @Test
        void quarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var options = cli("-s", "quarkus");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.args().stream().findFirst(), is(Optional.of("mvn")));
                assertThat(task.directory(), is(Path.of("quarkus/integration-tests")));
            });
        }

        @Test
        void quarkusPlatform()
        {
            final var os = new RecordingOperatingSystem();
            final var options = cli("-s", "quarkus-platform");
            MavenTest.Maven.test(options, os::record);

            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.directory(), is(Path.of("quarkus-platform")));
                final var arguments = new ArrayList<>(task.args());
                assertThat(arguments.stream().findFirst(), is(Optional.of("mvn")));
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

            return (T) result.subResult().results().get(0);
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

        static InMemoryFileSystem ofExists(Marker... markers)
        {
            final var map = Stream.of(markers)
                .collect(
                    Collectors.toMap(Function.identity(), marker -> true)
                );
            return new InMemoryFileSystem(map);
        }
    }

    private static final class RecordingOperatingSystem
    {
        private final Queue<Object> tasks = new ArrayDeque<>();

        void record(Tasks.Exec task)
        {
            offer(task);
        }

        private void offer(Object task)
        {
            final var success = tasks.offer(task);
            assertThat(success, is(true));
        }

        Path bootJdkHome()
        {
            return Path.of("boot/jdk/home");
        }

        void assertExecutedOneTask(String expected, Path directory, Marker result)
        {
            assertNumberOfTasks(1);
            assertTask(t ->
            {
                assertThat(t.directory(), is(directory));
                assertThat(
                    String.join(" ", t.args())
                    , is(equalTo(expected))
                );
            });
            assertThat(result.touched(), is(true));
            assertThat(
                result.path()
                , is(Path.of(String.format("%s.marker", Hashing.sha1(expected))))
            );
        }

        void assertNumberOfTasks(int size)
        {
            assertThat(tasks.toString(), tasks.size(), is(size));
        }

        void assertTask(Consumer<Tasks.Exec> asserts)
        {
            final Tasks.Exec head = peekTask();
            asserts.accept(head);
        }

        void assertAllTasks(Consumer<Tasks.Exec> asserts)
        {
            for (Object object : tasks)
            {
                if (object instanceof Tasks.Exec task)
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

    private static final class OptionsRecorder<T> implements Consumer<T>
    {
        T options;

        @Override
        public void accept(T options)
        {
            this.options = options;
        }
    }

    public static void main(String[] args)
    {
        QuarkusCheck.check();
    }
}
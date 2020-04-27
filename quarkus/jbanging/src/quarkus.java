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
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Command
public class quarkus implements Runnable
{
    @Spec
    CommandSpec spec;

    public static void main(String[] args)
    {
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.DEBUG);

        // Run checks
        QuarkusCheck.check();

        final var fs = FileSystem.ofSystem();
        final var os = new OperatingSystem(fs);

        final var quarkusClean = QuarkusClean.ofSystem(fs);
        final var quarkusTest = QuarkusTest.ofSystem(fs, os);
        final var quarkusBuild = QuarkusBuild.ofSystem(fs, os);

        int exitCode = new CommandLine(new quarkus())
            .addSubcommand("clean", quarkusClean)
            .addSubcommand("build", quarkusBuild)
            .addSubcommand("test", quarkusTest)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
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
    name = "build"
    , aliases = {"b"}
    , description = "Build quarkus."
    , mixinStandardHelpOptions = true
)
class QuarkusBuild implements Runnable
{
    // TODO add namespace info
    static final Logger LOG = LogManager.getLogger(QuarkusBuild.class);

    private final Consumer<Options> runner;

    @Option(
        defaultValue = "https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.0-b02"
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

    @Option(
        description = "Additional projects to build before Quarkus. Separated by comma(,) character."
        , names =
        {
            "-prb"
            , "--pre-build"
        }
        , split = ","
    )
    List<URI> preBuild = new ArrayList<>();

    @Option(
        description = "Additional projects to build after Quarkus. Separated by comma(,) character."
        , names =
        {
            "-pob"
            , "--post-build"
        }
        , split = ","
    )
    List<URI> postBuild = new ArrayList<>();

    @Option(
        defaultValue = "https://github.com/quarkusio/quarkus/tree/master"
        , description = "Quarkus source tree URL"
        , names =
        {
            "-qt"
            , "--quarkus-tree"
        }
    )
    URI quarkusTree;

    private QuarkusBuild(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static QuarkusBuild ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new QuarkusBuild(new QuarkusBuild.RunBuild(fs, os));
    }

    static QuarkusBuild of(Consumer<QuarkusBuild.Options> runner)
    {
        return new QuarkusBuild(runner);
    }

    @Override
    public void run()
    {
        LOG.info("Build");
        final var options = Options.of(
            jdkTree
            , mxTree
            , graalTree
            , preBuild
            , quarkusTree
            , postBuild
        );
        LOG.info(options);
        runner.accept(options);
    }

    final static class RunBuild implements Consumer<Options>
    {
        final FileSystem fs;
        final OperatingSystem os;

        RunBuild(FileSystem fs, OperatingSystem os)
        {
            this.fs = fs;
            this.os = os;
        }

        @Override
        public void accept(Options options)
        {
            Git.clone(Options.urls(options), fs::exists, os::exec, fs::touch);

            Java.build(options, os::bootJdkHome, fs::exists, os::exec, fs::touch);
            Java.link(options, fs::symlink);

            Graal.build(options, fs::exists, os::exec, fs::touch);
            Graal.link(options, fs::symlink);

            Maven.build(options, fs::exists, os::exec, fs::touch);
        }
    }

    record Options(
        Git.URL jdk
        , Git.URL mx
        , Git.URL graal
        , List<Git.URL> preBuild
        , Git.URL quarkus
        , List<Git.URL> postBuild
    )
    {
        static Options of(
            URI jdk
            , URI mx
            , URI graal
            , List<URI> preBuild
            , URI quarkus
            , List<URI> postBuild
        )
        {
            return new Options(
                Git.URL.of(jdk)
                , Git.URL.of(mx)
                , Git.URL.of(graal)
                , Git.URL.of(preBuild)
                , Git.URL.of(quarkus)
                , Git.URL.of(postBuild)
            );
        }

        static List<Git.URL> urls(Options options)
        {
            final var urls = new ArrayList<Git.URL>();
            urls.add(options.jdk);
            urls.add(options.mx);
            urls.add(options.graal);
            urls.addAll(options.preBuild);
            urls.add(options.quarkus);
            urls.addAll(options.postBuild);
            return urls;
        }
    }

    record Java(Java.Type type, Path name)
    {
        static Java of(Options options)
        {
            final var type = type(options.jdk);
            final var name = Path.of(options.jdk.name());
            return new Java(type, name);
        }

        static Marker build(
            Options options
            , Supplier<Path> bootJdkHome
            , Predicate<Marker> exists
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var task = Java.toBuild(options, bootJdkHome, exists);
            return Java.doBuild(task, exec, touch);
        }

        private static OperatingSystem.MarkerMultiTask toBuild(
            Options options
            , Supplier<Path> bootJdkHome
            , Predicate<Marker> exists
        )
        {
            final var java = Java.of(options);
            final var marker = Marker.build(java.name).query(exists);
            if (marker.exists())
            {
                return new OperatingSystem.MarkerMultiTask(Stream.empty(), marker);
            }

            final var tasks = switch (java.type)
                {
                    case OPENJDK -> Java.OpenJDK.buildSteps(java, bootJdkHome);
                    case LABSJDK -> Java.LabsJDK.buildSteps(java, bootJdkHome);
                };

            return new OperatingSystem.MarkerMultiTask(tasks, marker);
        }

        private static Marker doBuild(
            OperatingSystem.MarkerMultiTask task
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            task.task().forEach(exec);
            final var marker = task.marker();
            return marker.touch(touch);
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var java = Java.of(options);

            final var target =
                switch (java.type)
                    {
                        case OPENJDK -> Java.OpenJDK.javaHome(java);
                        case LABSJDK -> Java.LabsJDK.javaHome(java);
                    };

            final var link = Homes.java();
            LOG.info("Link {} to {}", link, target);
            return symLink.apply(link, target);
        }

        private static final class OpenJDK
        {
            static Stream<OperatingSystem.Task> buildSteps(
                Java java
                , Supplier<Path> bootJdkHome
            )
            {
                return Stream.of(
                    configureSh(java, bootJdkHome)
                    , makeGraalJDK(java)
                );
            }

            static Path javaHome(Java java)
            {
                return java.name.resolve(
                    Path.of(
                         "build"
                        , "graal-server-release"
                        , "images"
                        , "graal-builder-jdk"
                    )
                );
            }

            private static OperatingSystem.Task configureSh(Java java, Supplier<Path> bootJdkHome)
            {
                return new OperatingSystem.Task(
                    Stream.of(
                        "sh"
                        , "configure"
                        , "--with-conf-name=graal-server-release"
                        , "--disable-warnings-as-errors"
                        , "--with-jvm-features=graal"
                        , "--with-jvm-variants=server"
                        , "--enable-aot=no"
                        , String.format("--with-boot-jdk=%s", bootJdkHome.get())
                    )
                    , java.name
                    , Stream.empty()
                );
            }

            private static OperatingSystem.Task makeGraalJDK(Java java)
            {
                return new OperatingSystem.Task(
                    Stream.of(
                        "make"
                        , "graal-builder-image"
                    )
                    , java.name
                    , Stream.empty()
                );
            }
        }

        private static final class LabsJDK
        {
            static Stream<OperatingSystem.Task> buildSteps(Java java, Supplier<Path> bootJdkPath)
            {
                return Stream.of(buildJDK(java, bootJdkPath));
            }

            static Path javaHome(Java java)
            {
                return java.name.resolve("java_home");
            }

            private static OperatingSystem.Task buildJDK(Java java, Supplier<Path> bootJdkPath)
            {
                return new OperatingSystem.Task(
                    Stream.of(
                        "python"
                        , "build_labsjdk.py"
                    )
                    , java.name
                    , Stream.of(Homes.EnvVars.bootJava(bootJdkPath))
                );
            }
        }

        private static Java.Type type(Git.URL url)
        {
            return switch (url.organization())
                {
                    case "openjdk" -> Type.OPENJDK;
                    case "graalvm" -> Type.LABSJDK;
                    default -> throw new IllegalStateException(
                        "Unexpected value: " + url.name()
                    );
                };
        }

        enum Type
        {
            OPENJDK, LABSJDK
        }
    }

    record Graal(Path name, Path mx)
    {
        static Graal of(Options options)
        {
            final var name = Path.of(options.graal.name());
            final var mx = Path.of(options.mx.name(), "mx");
            return new Graal(name, mx);
        }

        static Marker build(
            Options options
            , Predicate<Marker> exists
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var graal = Graal.of(options);
            final var task = Graal.toBuild(graal, exists);
            return Graal.doBuild(task, exec, touch);
        }

        private static OperatingSystem.MarkerTask toBuild(Graal graal, Predicate<Marker> exists)
        {
            final var marker = Marker.build(graal.name).query(exists);
            if (marker.exists())
            {
                return OperatingSystem.MarkerTask.noop(marker);
            }

            return buildTask(graal, marker);
        }

        private static Marker doBuild(
            OperatingSystem.MarkerTask task
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            if (OperatingSystem.Task.isNoop(task.task()))
                return task.marker();

            exec.accept(task.task());
            final var marker = task.marker();
            return marker.touch(touch);
        }

        private static OperatingSystem.MarkerTask buildTask(Graal graal, Marker marker)
        {
            return new OperatingSystem.MarkerTask(
                new OperatingSystem.Task(
                    Stream.of(
                        Path.of("../..").resolve(graal.mx).toString()
                        , "build"
                    )
                    , Graal.svm(graal)
                    , Stream.of(Homes.EnvVars.java())
                )
                , marker
            );
        }

        static Link link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var graal = Graal.of(options);

            final var target = graal.name.resolve(
                Path.of(
                    "sdk"
                    , "latest_graalvm_home"
                )
            );

            return symLink.apply(Homes.graal(), target);
        }

        static Path svm(Graal graal)
        {
            return graal.name.resolve("substratevm");
        }
    }

    record Maven(List<Path>projects)
    {
        static Maven of(Options options)
        {
            final var urls = new ArrayList<>(options.preBuild);
            urls.add(options.quarkus);
            urls.addAll(options.postBuild);

            final var projects = urls.stream()
                .map(Git.URL::name)
                .map(Path::of)
                .collect(Collectors.toList());

            return new Maven(projects);
        }

        static List<Marker> build(
            Options options
            , Predicate<Marker> exists
            , Function<OperatingSystem.MarkerTask, Marker> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var maven = Maven.of(options);
            final var tasks = Maven.toBuild(maven, exists);
            return Maven.doBuild(tasks, exec, touch);
        }

        private static Stream<OperatingSystem.MarkerTask> toBuild(
            Maven maven
            , Predicate<Marker> exists
        )
        {
            return maven.projects.stream()
                .map(Marker::build)
                .filter(marker -> !marker.query(exists).exists())
                .map(Maven::buildTask);
        }

        private static OperatingSystem.MarkerTask buildTask(Marker marker)
        {
            return new OperatingSystem.MarkerTask(
                new OperatingSystem.Task(
                    Stream.of(
                        "mvn" // ?
                        , "install"
                        , "-DskipTests"
                        , "-Dformat.skip"
                    )
                    , marker.path().getParent()
                    , Stream.of(Homes.EnvVars.graal())
                )
                , marker
            );
        }

        private static List<Marker> doBuild(
            Stream<OperatingSystem.MarkerTask> tasks
            , Function<OperatingSystem.MarkerTask, Marker> exec
            , Function<Marker, Boolean> touch
        )
        {
            return tasks
                .map(exec)
                .map(marker -> marker.touch(touch))
                .collect(Collectors.toList());
        }
    }
}

@Command(
    name = "test"
    , aliases = {"t"}
    , description = "Test quarkus."
    , mixinStandardHelpOptions = true
)
class QuarkusTest implements Runnable
{
    private static final Logger LOG = LogManager.getLogger(QuarkusTest.class);

    private final Consumer<Options> runner;

    @Option(
        description = """
            Test suites to only run. By default only quarkus.
            If suites provided, only those provided in the list are executed.
            Other suites can be referenced using the repository name, e.g quarkus-platform.
            The order of suites represents the order test execution.
            """
        , names =
        {
            "-s"
            , "--suites"
        }
        , split = ","
    )
    private final List<String> suites = new ArrayList<>();

    @Option(
        description = """
            Additional test URLs to download and run.
            The order of URLs determines the order of test execution.
            """
        , names =
        {
            "-at"
            , "--also-test"
        }
        , split = ","
    )
    private final List<URI> alsoTest = new ArrayList<>();

    @Option(
        description = """
             Additional test arguments, each argument separated by comma (',') per suite.
             Multiple occurrences are allowed to support different suites. 
             Example: quarkus-platform=-rf,:aws,-Dquarkus.version=999-SNAPSHOT
            """
        , names =
        {
            "-ata"
            , "--additional-test-args"
        }
    )
    private final List<String> additionalTestArgs = new ArrayList<>();

    private QuarkusTest(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static QuarkusTest ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new QuarkusTest(new RunTest(fs, os));
    }

    static QuarkusTest of(Consumer<Options> runner)
    {
        return new QuarkusTest(runner);
    }

    @Override
    public void run()
    {
        var options = Options.of(suites, alsoTest, additionalTestArgs);
        LOG.info(options);
        runner.accept(options);
    }

    final static class RunTest implements Consumer<Options>
    {
        final FileSystem fs;
        final OperatingSystem os;

        RunTest(FileSystem fs, OperatingSystem os)
        {
            this.fs = fs;
            this.os = os;
        }

        @Override
        public void accept(Options options)
        {
            Git.clone(options.alsoTest, fs::exists, os::exec, fs::touch);
            Maven.test(options, os::exec);
        }
    }

    record Options(
        List<String>suites
        , List<Git.URL>alsoTest
        , Map<String, Arguments>testArgs
    )
    {
        static Options of(
            List<String> suites
            , List<URI> alsoTest
            , List<String> additionalTestArgs
        )
        {
            return new Options(
                suites
                , Git.URL.of(alsoTest)
                , Arguments.of(additionalTestArgs)
            );
        }
    }

    record Arguments(List<String>arguments)
    {
        static Map<String, Arguments> of(List<String> arguments)
        {
            return arguments.stream()
                .map(args -> args.split("=", 2))
                .collect(
                    Collectors.toMap(
                        split -> split[0]
                        , split -> Arguments.of(split[1])
                    )
                );
        }

        private static Arguments of(String value)
        {
            return new Arguments(Arrays.asList(value.split(",")));
        }
    }

    record Maven(List<String>suites, Map<String, Arguments>testArgs)
    {
        static Maven of(Options options)
        {
            final var suites = suites(options);
            final var testArgs = options.testArgs();
            return new Maven(suites, testArgs);
        }

        private static List<String> suites(Options options)
        {
            if (!options.suites.isEmpty())
                return options.suites;

            final var suites = new ArrayList<>(List.of("quarkus"));
            final var alsoTestSuites = options.alsoTest.stream()
                .map(Git.URL::name)
                .collect(Collectors.toList());
            suites.addAll(alsoTestSuites);
            return suites;
        }

        static void test(Options options, Consumer<OperatingSystem.Task> exec)
        {
            final var tasks = Maven.toTest(options);
            Maven.doTest(tasks, exec);
        }

        private static void doTest(
            Stream<OperatingSystem.Task> tasks
            , Consumer<OperatingSystem.Task> exec
        )
        {
            tasks.forEach(exec);
        }

        private static Stream<OperatingSystem.Task> toTest(Options options)
        {
            final var maven = Maven.of(options);
            return maven.suites.stream()
                .map(suiteTest(maven));
        }

        private static Function<String, OperatingSystem.Task> suiteTest(Maven maven)
        {
            return suite ->
            {
                final var args = maven.testArgs.get(suite);

                return new OperatingSystem.Task(
                    Stream.concat(
                        Stream.of(
                            "mvn" // ?
                            , "install"
                            , "-Dnative"
                            , "-Dformat.skip"
                        )
                        , Objects.isNull(args)
                            ? Stream.empty()
                            : args.arguments.stream()
                    )
                    , Maven.suitePath(suite)
                    , Stream.of(Homes.EnvVars.graal())
                );
            };
        }

        static Path suitePath(String suite)
        {
            return suite.equals("quarkus")
                ? Path.of("quarkus", "integration-tests")
                : Path.of(suite);
        }
    }
}

class Git
{
    record URL(
        String organization
        , String name // TODO refactor to repoName or repository
        , String branch
        , String url
    )
    {
        static Git.URL of(URI uri)
        {
            final var path = Path.of(uri.getPath());

            final var organization = path.getName(0).toString();
            final var name = path.getName(1).toString();
            final var branch = path.getFileName().toString();
            final var url = uri.resolve("..").toString();

            return new URL(organization, name, branch, url);
        }

        static List<Git.URL> of(List<URI> uris)
        {
            return uris.stream()
                .map(Git.URL::of)
                .collect(Collectors.toList());
        }
    }

    record MarkerURL(Git.URL url, Marker marker) {}

    static List<Marker> clone(
        List<Git.URL> urls
        , Predicate<Marker> exists
        , Function<OperatingSystem.MarkerTask, Marker> exec
        , Function<Marker, Boolean> touch
    )
    {
        final var tasks = Git.toClone(urls, exists);
        return Git.doClone(tasks, exec, touch);
    }

    private static List<Marker> doClone(
        Stream<OperatingSystem.MarkerTask> tasks
        , Function<OperatingSystem.MarkerTask, Marker> exec
        , Function<Marker, Boolean> touch
    )
    {
        return tasks
            .map(exec)
            .map(marker -> marker.touch(touch))
            .collect(Collectors.toList());
    }

    private static Stream<OperatingSystem.MarkerTask> toClone(
        List<Git.URL> urls
        , Predicate<Marker> exists
    )
    {
        return urls.stream()
            .map(Git::markerURL)
            .filter(Git.needsDownload(exists))
            .map(Git::cloneTask);
    }

    private static Git.MarkerURL markerURL(Git.URL url)
    {
        return new Git.MarkerURL(url, Marker.download(url.name()));
    }

    private static OperatingSystem.MarkerTask cloneTask(Git.MarkerURL url)
    {
        return new OperatingSystem.MarkerTask(
            new OperatingSystem.Task(
                Git.toClone(url.url())
                , Path.of("")
                , Stream.empty())
            , url.marker()
        );
    }

    private static Predicate<Git.MarkerURL> needsDownload(Predicate<Marker> exists)
    {
        return url -> !url.marker.query(exists).exists();
    }

    static Stream<String> toClone(Git.URL url)
    {
        return Stream.of(
            "git"
            , "clone"
            , "-b"
            , url.branch
            , "--depth"
            , "10"
            , url.url
        );
    }
}

class Homes
{
    static Path java()
    {
        return Path.of("java_home");
    }

    static Path graal()
    {
        return Path.of("graal_home");
    }

    static class EnvVars
    {
        static OperatingSystem.EnvVar java()
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , root -> root.resolve(Homes.java())
            );
        }

        static OperatingSystem.EnvVar graal()
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , root -> root.resolve(Homes.graal())
            );
        }

        static OperatingSystem.EnvVar bootJava(Supplier<Path> path)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , ignore -> path.get()
            );
        }
    }
}

// Boundary value
record Marker(boolean exists, boolean touched, Path path)
{
    static final Logger LOG = LogManager.getLogger(Marker.class);

    Marker query(Predicate<Marker> existsFn)
    {
        final var exists = existsFn.test(this);
        if (exists)
        {
            LOG.info("Path exists {}", path);
            return new Marker(true, this.touched, this.path);
        }

        LOG.info("Path does not exist {}", path);
        return new Marker(false, this.touched, this.path);
    }

    Marker touch(Function<Marker, Boolean> touchFn)
    {
        if (exists)
        {
            LOG.info("Skip touch, path exists {}", path);
            return this;
        }

        final var touched = touchFn.apply(this);
        if (touched)
        {
            LOG.info("Touched path {}", path);
            return new Marker(true, true, path);
        }

        LOG.info("Could not touch path {}", path);
        return new Marker(false, false, path);
    }

    static Marker build(Path path)
    {
        return new Marker(false, false, path.resolve("build.marker"));
    }

    static Marker download(String dirName)
    {
        return new Marker(false, false, Path.of(dirName, "download.marker"));
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
        var formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        var today = date.format(formatter);
        LOG.info("Today is {}", today);

        var baseDir = Path.of(
            System.getProperty("user.home")
            , "workspace"
            , "quarkus-with-graal"
        );
        LOG.info("Base directory: {}", baseDir);

        final var path = baseDir.resolve(today);
        LOG.info("Root path: {}", path);

        return new FileSystem(mkdirs(path));
    }

    private static Path mkdirs(Path path)
    {
        final var directory = path.toFile();
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new RuntimeException(String.format(
                "Unable to create path: %s"
                , path)
            );
        }

        return path;
    }

    private FileSystem(Path root)
    {
        this.root = root;
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
        final var target = root.resolve(relativeTarget);
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

    Marker exec(MarkerTask task)
    {
        exec(task.task);
        return task.marker;
    }

    void exec(Task task)
    {
        final var taskList = task.task
            .filter(Predicate.not(String::isEmpty))
            .collect(Collectors.toList());

        final var directory = fs.root.resolve(task.directory);
        LOG.debug("Execute {} in {}", taskList, directory);
        try
        {
            var processBuilder = new ProcessBuilder(taskList)
                .directory(directory.toFile())
                .inheritIO();

            task.envVars.forEach(
                envVar -> processBuilder.environment()
                    .put(envVar.name, envVar.value.apply(fs.root).toString())
            );

            Process process = processBuilder.start();

            if (process.waitFor()!=0)
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

    record Task(Stream<String>task, Path directory, Stream<EnvVar>envVars)
    {
        private static final Task NOOP = new Task(
            Stream.empty()
            , Path.of("")
            , Stream.empty()
        );

        static Task noop()
        {
            return NOOP;
        }

        static boolean isNoop(Task task)
        {
            return task == NOOP;
        }
    }

    record MarkerTask(Task task, Marker marker)
    {
        static MarkerTask noop(Marker marker)
        {
            return new MarkerTask(Task.noop(), marker);
        }
    }

    // TODO retrofit stream into marker task
    record MarkerMultiTask(Stream<Task>task, Marker marker) {}

    record EnvVar(String name, Function<Path, Path>value) {}
}

final class QuarkusCheck
{
    static void check()
    {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectClass(CheckGit.class)
                , selectClass(CheckBuild.class)
                , selectClass(CheckTest.class)
            )
            .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        listener.getSummary().printTo(new PrintWriter(System.out));
        listener.getSummary().printFailuresTo(new PrintWriter(System.err));
        if (listener.getSummary().getTestsFailedCount() > 0)
            throw new AssertionError("Expected no failures");
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
            final var url = Git.URL.of(URI.create("https://github.com/openjdk/jdk11u-dev/tree/master"));
            assertThat(url.organization(), is("openjdk"));
            assertThat(url.name(), is("jdk11u-dev"));
            assertThat(url.branch(), is("master"));
            assertThat(url.url(), is("https://github.com/openjdk/jdk11u-dev/"));
        }

        @Test
        void cloneDefault()
        {
            final var os = new RecordingOperatingSystem();
            final List<Git.URL> urls = Collections.emptyList();
            final var cloned = Git.clone(urls, m -> false, os::record, m -> false);
            os.assertNumberOfTasks(0);
            assertThat(cloned.size(), is(0));
        }

        @Test
        void cloneSelective()
        {
            final var fs = new InMemoryFileSystem(
                Map.of(
                    Marker.download("repo-a"), true
                    , Marker.download("repo-b"), false
                )
            );
            final var os = new RecordingOperatingSystem();
            final List<Git.URL> urls = Git.URL.of(
                List.of(
                    URI.create("h://_/_/repo-a")
                    , URI.create("h:/_/_/repo-b")
                )
            );
            final var cloned = Git.clone(urls, fs::exists, os::record, fs::touch);
            os.assertNumberOfTasks(1);
            os.assertMarkerTask(t -> assertThat(t.task().task().findFirst(), is(Optional.of("git"))));
            assertThat(cloned.size(), is(1));
            assertThat(cloned.get(0).touched(), is(true));
        }
    }

    @ExtendWith(LoggingExtension.class)
    static class CheckBuild
    {
        @Test
        void javaOpenJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = executeCli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev"
            );

            final var marker = QuarkusBuild.Java.build(
                options
                , os::bootJdkHome
                , fs::exists
                , os::record, m -> true
            );
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(2);
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("sh")));
                assertThat(task.directory(), is(Path.of("jdk11u-dev")));
            });
            os.forward();
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("make")));
                assertThat(task.directory(), is(Path.of("jdk11u-dev")));
            });
        }

        @Test
        void skipJavaOpenJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.of(
                Marker.build(Path.of("jdk11u-dev")), true
            );
            final var options = executeCli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev"
            );

            final var marker = QuarkusBuild.Java.build(
                options
                , os::bootJdkHome
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
            os.assertNumberOfTasks(0);
        }

        @Test
        void maven()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = new InMemoryFileSystem(Collections.emptyMap());
            final var options = executeCli();
            final var markers = QuarkusBuild.Maven.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(markers.size(), is(1));
            assertThat(markers.get(0).exists(), is(true));
            assertThat(markers.get(0).touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertMarkerTask(task ->
            {
                assertThat(task.task().task().findFirst(), is(Optional.of("mvn")));
                assertThat(task.task().directory(), is(Path.of("quarkus")));
            });
        }

        @Test
        void skipMaven()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.of(
                Marker.build(Path.of("quarkus")), true
            );
            final var options = executeCli();
            final var markers = QuarkusBuild.Maven.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(markers.size(), is(0));
            os.assertNumberOfTasks(0);
        }

        @Test
        void graalLink()
        {
            final var options = executeCli();
            final var linked = QuarkusBuild.Graal.link(
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
            final var options = executeCli();
            final var marker = QuarkusBuild.Graal.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("../../mx/mx")));
                assertThat(task.directory(), is(Path.of("graal/substratevm")));
            });
        }

        @Test
        void skipGraalBuild()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.of(
                Marker.build(Path.of("graal")), true
            );
            final var options = executeCli();
            final var marker = QuarkusBuild.Graal.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(false));
            os.assertNumberOfTasks(0);
        }

        @Test
        void labsJDKLink()
        {
            final var options = executeCli();
            final var linked = QuarkusBuild.Java.link(
                options
                , Link::new
            );
            assertThat(linked.link(), is(Homes.java()));
            assertThat(linked.target(), is(Path.of("labs-openjdk-11", "java_home")));
        }

        @Test
        void openJDKLink()
        {
            final var options = executeCli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev"
            );
            final var linked = QuarkusBuild.Java.link(
                options
                , Link::new
            );
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
        void testJavaBuildDefault()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = new InMemoryFileSystem(
                Map.of(
                    Marker.download("labs-openjdk-11"), false
                )
            );
            final var options = executeCli();

            final var marker = QuarkusBuild.Java.build(
                options
                , os::bootJdkHome
                , fs::exists
                , os::record, m -> true
            );
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("python")));
                assertThat(task.directory(), is(Path.of("labs-openjdk-11")));
            });
        }

        private static QuarkusBuild.Options executeCli(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("build");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder();
            final var quarkusBuild = QuarkusBuild.of(recorder);
            new CommandLine(new quarkus())
                .addSubcommand("build", quarkusBuild)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
            return recorder.options;
        }

        private static final class OptionsRecorder implements Consumer<QuarkusBuild.Options>
        {
            QuarkusBuild.Options options;

            @Override
            public void accept(QuarkusBuild.Options options)
            {
                this.options = options;
            }
        }
    }

    static class CheckTest
    {
        @Test
        void cliAdditionalTestArgsOptions()
        {
            assertThat(
                execute("-ata", "a=b,:c", "-ata", "z=-y,-Dx=w").testArgs()
                , is(equalTo(QuarkusTest.Arguments.of(
                    List.of(
                        "a=b,:c"
                        , "z=-y,-Dx=w"
                    )
                )))
            );
            assertThat(
                execute("--additional-test-args", "a=b,:c", "--additional-test-args", "z=-y,-Dx=w").testArgs()
                , is(equalTo(QuarkusTest.Arguments.of(
                    List.of(
                        "a=b,:c"
                        , "z=-y,-Dx=w"
                    )
                )))
            );
        }

        @Test
        void cliAlsoTestOptions()
        {
            assertThat(
                execute("-at", "h://g/a/b,h://g/b/c").alsoTest()
                , is(equalTo(Git.URL.of(List.of(
                    URI.create("h://g/a/b")
                    , URI.create("h://g/b/c")
                ))))
            );
            assertThat(
                execute("--also-test", "h://g/w/x,h://g/y/z").alsoTest()
                , is(equalTo(Git.URL.of(List.of(
                    URI.create("h://g/w/x")
                    , URI.create("h://g/y/z")
                ))))
            );
        }

        @Test
        void cliSuiteOptions()
        {
            assertThat(
                execute("-s", "a,b").suites()
                , is(equalTo(List.of("a", "b")))
            );
            assertThat(
                execute("--suites", "y,z").suites()
                , is(equalTo(List.of("y", "z")))
            );
        }

        @Test
        void cliEmptyOptions()
        {
            final var command = execute();
            assertThat(command.suites(), is(empty()));
            assertThat(command.alsoTest(), is(empty()));
            assertThat(command.testArgs(), is(anEmptyMap()));
        }

        private static QuarkusTest.Options execute(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("test");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder();
            final var quarkusTest = QuarkusTest.of(recorder);
            new CommandLine(new quarkus())
                .addSubcommand("test", quarkusTest)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
            return recorder.options;
        }

        @Test
        void testArguments()
        {
            final var options = QuarkusTest.Options.of(
                List.of("suite-a", "suite-b")
                , Collections.emptyList()
                , List.of("suite-a=p1,:p2", "suite-b=:p3,-p4")
            );
            final var os = test(options);
            os.assertNumberOfTasks(2);
            os.assertTask(t ->
                assertThat(
                    t.task().collect(Collectors.joining(" "))
                    , is(equalTo("mvn install -Dnative -Dformat.skip p1 :p2"))
                )
            );
            os.forward();
            os.assertTask(t ->
                assertThat(
                    t.task().collect(Collectors.joining(" "))
                    , is(equalTo("mvn install -Dnative -Dformat.skip :p3 -p4"))
                )
            );
        }

        @Test
        void testSuites()
        {
            final var options = QuarkusTest.Options.of(
                List.of("suite-a", "suite-b")
                , Collections.emptyList()
                , Collections.emptyList()
            );
            final var os = test(options);
            os.assertNumberOfTasks(2);
            os.assertAllTasks(t -> assertThat(t.task().findFirst(), is(Optional.of("mvn"))));
            os.assertTask(t -> assertThat(t.directory(), is(Path.of("suite-a"))));
            os.forward();
            os.assertTask(t -> assertThat(t.directory(), is(Path.of("suite-b"))));
        }

        @Test
        void testDefault()
        {
            final var options = QuarkusTest.Options.of(
                Collections.emptyList()
                , Collections.emptyList()
                , Collections.emptyList()
            );
            final var os = test(options);
            os.assertNumberOfTasks(1);
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("mvn")));
                assertThat(task.directory(), is(Path.of("quarkus/integration-tests")));
            });
        }

        private static final class OptionsRecorder implements Consumer<QuarkusTest.Options>
        {
            QuarkusTest.Options options;

            @Override
            public void accept(QuarkusTest.Options options)
            {
                this.options = options;
            }
        }

        private static RecordingOperatingSystem test(QuarkusTest.Options options)
        {
            final var os = new RecordingOperatingSystem();
            QuarkusTest.Maven.test(options, os::record);
            return os;
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

        static InMemoryFileSystem of(Marker marker, boolean exists)
        {
            return new InMemoryFileSystem(Map.of(marker, exists));
        }
    }

    private static final class RecordingOperatingSystem
    {
        private final Queue<Object> tasks = new ArrayDeque<>();

        void record(OperatingSystem.Task task)
        {
            offer(task);
        }

        private void offer(Object task)
        {
            final var success = tasks.offer(task);
            assertThat(success, is(true));
        }

        Marker record(OperatingSystem.MarkerTask task)
        {
            offer(task);
            return task.marker();
        }

        Path bootJdkHome()
        {
            return Path.of("boot/jdk/home");
        }

        void assertNumberOfTasks(int size)
        {
            assertThat(tasks.size(), is(size));
        }

        void assertTask(Consumer<OperatingSystem.Task> asserts)
        {
            final OperatingSystem.Task head = peekTask();
            asserts.accept(head);
        }

        void assertMarkerTask(Consumer<OperatingSystem.MarkerTask> asserts)
        {
            final OperatingSystem.MarkerTask head = peekTask();
            asserts.accept(head);
        }

        void assertAllTasks(Consumer<OperatingSystem.Task> asserts)
        {
            for (Object object : tasks)
            {
                if (object instanceof OperatingSystem.Task task)
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

    public static void main(String[] args)
    {
        QuarkusCheck.check();
    }
}
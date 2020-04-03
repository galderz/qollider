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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
        final var quarkusTest = new QuarkusTest(fs, new OperatingSystem(fs));

        int exitCode = new CommandLine(new quarkus())
            .addSubcommand("clean", new QuarkusClean())
            .addSubcommand("build", new QuarkusBuild())
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

    @Override
    public void run()
    {
        LOG.info("Clean!");
        clean(projects, Root.newSystemRoot());
    }

    static void clean(List<String> projects, Root root)
    {
        if (projects.isEmpty())
        {
            DeprecatedOperatingSystem.deleteRecursive()
                .compose(Root::path)
                .apply(root);
        }
        else
        {
            projects.forEach(projectName ->
                DeprecatedOperatingSystem.deleteRecursive()
                    .compose((Path path) -> path.resolve(projectName))
                    .compose(Root::path)
                    .apply(root));
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
    static final Logger LOG = LogManager.getLogger(QuarkusClean.class);

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

    @Override
    public void run()
    {
        LOG.info("Build");
        final var root = Root.newSystemRoot();
        final var options = new Options(
            DeprecatedGit.URL.of(jdkTree)
            , DeprecatedGit.URL.of(mxTree)
            , DeprecatedGit.URL.of(graalTree)
            , DeprecatedGit.URL.of(preBuild)
            , DeprecatedGit.URL.of(quarkusTree)
            , DeprecatedGit.URL.of(postBuild)
        );
        LOG.info(options);

        final var urls = Options.urls(options);
        DeprecatedGit.download(urls, root);

        final var java = Java.newSystemJava(options.jdk);
        Java.build(java, root);
        Java.link(java, root);

        final var graal = Graal.of(options);
        Graal.build(graal, root);
        Graal.link(graal, root);

        final var maven = Maven.of(options);
        Maven.build(maven, root);
    }

    private record Options(
        DeprecatedGit.URL jdk
        , DeprecatedGit.URL mx
        , DeprecatedGit.URL graal
        , List<DeprecatedGit.URL>preBuild
        , DeprecatedGit.URL quarkus
        , List<DeprecatedGit.URL>postBuild
    )
    {
        static List<DeprecatedGit.URL> urls(Options options)
        {
            final var merged = new ArrayList<DeprecatedGit.URL>();
            merged.add(options.jdk);
            merged.add(options.mx);
            merged.add(options.graal);
            merged.addAll(options.preBuild);
            merged.add(options.quarkus);
            merged.addAll(options.postBuild);
            return merged;
        }
    }

    private record Java(DeprecatedGit.URL url, Path bootJdk)
    {
        static Java newSystemJava(DeprecatedGit.URL url)
        {
            var bootJDK = Path.of(System.getenv("BOOT_JDK_HOME"));
            return new Java(url, bootJDK);
        }

        static void build(Java java, Root root)
        {
            final var marker = DeprecatedMarker.build(java.url.name(), root);
            if (DeprecatedMarker.skip(marker))
                return;

            final var javaType = type(java.url);

            final var steps =
                switch (javaType)
                    {
                        case OPENJDK -> Java.OpenJDK.buildSteps(java, root);
                        case LABSJDK -> Java.LabsJDK.buildSteps(java, root);
                    };

            steps.forEach(DeprecatedOperatingSystem::exec);

            DeprecatedMarker.touch(marker);
        }

        static void link(Java java, Root root)
        {
            final var javaType = type(java.url);

            final var target =
                switch (javaType)
                    {
                        case OPENJDK -> Java.OpenJDK.javaHome(java, root);
                        case LABSJDK -> Java.LabsJDK.javaHome(java, root);
                    };

            final var link = Homes.java(root);
            DeprecatedOperatingSystem.symlink(link, target);
        }

        static Path root(Java java, Root root)
        {
            return root.path().resolve(
                Path.of(java.url.name())
            );
        }

        static DeprecatedOperatingSystem.EnvVar bootJdkEnvVar(Java java)
        {
            return new DeprecatedOperatingSystem.EnvVar(
                "JAVA_HOME"
                , java.bootJdk.toString()
            );
        }

        private static final class OpenJDK
        {
            static Stream<DeprecatedOperatingSystem.Command> buildSteps(Java java, Root root)
            {
                return Stream.of(
                    configureSh(java, root)
                    , makeGraalJDK(java, root)
                );
            }

            static Path javaHome(Java java, Root root)
            {
                final var os = DeprecatedOperatingSystem.type().toString().toLowerCase();
                final var arch = "x86_64";
                final var subpath = Path.of(
                    java.url.name()
                    , "build"
                    , String.format("%s-%s-normal-server-release", os, arch)
                    , "images"
                    , "graal-builder-jdk"
                );
                return root.path().resolve(subpath);
            }

            private static DeprecatedOperatingSystem.Command configureSh(Java java, Root root)
            {
                return new DeprecatedOperatingSystem.Command(
                    Stream.of(
                        "sh"
                        , "configure"
                        , "--disable-warnings-as-errors"
                        , "--with-jvm-features=graal"
                        , "--with-jvm-variants=server"
                        , "--enable-aot=no"
                        , String.format("--with-boot-jdk=%s", java.bootJdk.toString())
                    )
                    , Java.root(java, root)
                    , Stream.empty()
                );
            }

            private static DeprecatedOperatingSystem.Command makeGraalJDK(Java java, Root root)
            {
                return new DeprecatedOperatingSystem.Command(
                    Stream.of(
                        "make"
                        , "graal-builder-image"
                    )
                    , Java.root(java, root)
                    , Stream.empty()
                );
            }
        }

        private static final class LabsJDK
        {
            static Stream<DeprecatedOperatingSystem.Command> buildSteps(Java java, Root root)
            {
                return Stream.of(buildJDK(java, root));
            }

            static Path javaHome(Java java, Root root)
            {
                final var subpath = Path.of(
                    java.url.name()
                    , "java_home"
                );
                return root.path().resolve(subpath);
            }

            private static DeprecatedOperatingSystem.Command buildJDK(Java java, Root root)
            {
                return new DeprecatedOperatingSystem.Command(
                    Stream.of(
                        "python"
                        , "build_labsjdk.py"
                    )
                    , Java.root(java, root)
                    , Stream.of(Java.bootJdkEnvVar(java))
                );
            }
        }

        private static Java.Type type(DeprecatedGit.URL url)
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

    private record Graal(DeprecatedGit.URL graalURL, DeprecatedGit.URL mxURL)
    {
        static Graal of(Options options)
        {
            final var graalURL = options.graal;
            final var mxURL = options.mx;
            return new Graal(graalURL, mxURL);
        }

        static void build(Graal graal, Root root)
        {
            final var marker = DeprecatedMarker.build(graal.graalURL.name(), root);
            if (DeprecatedMarker.skip(marker))
                return;

            DeprecatedOperatingSystem.exec()
                .compose(Graal.mxbuild(graal))
                .apply(root);

            DeprecatedMarker.touch(marker);
        }

        static void link(Graal graal, Root root)
        {
            final var target = root.path().resolve(
                Path.of(
                    graal.graalURL.name()
                    , "sdk"
                    , "latest_graalvm_home"
                )
            );
            final var link = Homes.graal(root);
            DeprecatedOperatingSystem.symlink(link, target);
        }

        static Path mx(Graal graal, Root root)
        {
            return root.path().resolve(
                Path.of(graal.mxURL.name(), "mx")
            );
        }

        static Path svm(Graal graal, Root root)
        {
            return root.path().resolve(
                Path.of(graal.graalURL.name(), "substratevm")
            );
        }

        private static Function<Root, DeprecatedOperatingSystem.Command> mxbuild(Graal graal)
        {
            return root ->
                new DeprecatedOperatingSystem.Command(
                    Stream.of(
                        Graal.mx(graal, root).toString()
                        , "build"
                    )
                    , Graal.svm(graal, root)
                    , Stream.of(Homes.EnvVars.java(root))
                );
        }
    }

    private record Maven(List<DeprecatedGit.URL>projects)
    {
        static Maven of(Options options)
        {
            final var projects = new ArrayList<>(options.preBuild);
            projects.add(options.quarkus);
            projects.addAll(options.postBuild);
            return new Maven(projects);
        }

        static void build(Maven maven, Root root)
        {
            maven.projects
                .forEach(Maven.build(root));
        }

        static Consumer<DeprecatedGit.URL> build(Root root)
        {
            return url ->
            {
                final var marker = DeprecatedMarker.build(url.name(), root);
                if (DeprecatedMarker.skip(marker))
                    return;

                DeprecatedOperatingSystem.exec()
                    .compose(Maven.mvnInstall(url))
                    .apply(root);

                DeprecatedMarker.touch(marker);
            };
        }

        private static Function<Root, DeprecatedOperatingSystem.Command> mvnInstall(DeprecatedGit.URL url)
        {
            return root ->
                new DeprecatedOperatingSystem.Command(
                    Stream.of(
                        "mvn" // ?
                        , "install"
                        , "-DskipTests"
                        , "-Dformat.skip"
                    )
                    , root.path().resolve(url.name())
                    , Stream.of(Homes.EnvVars.deprecatedGraal(root))
                );
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

    final FileSystem fs;
    final OperatingSystem os;

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
    List<String> suites = new ArrayList<>();

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
    List<URI> alsoTest = new ArrayList<>();

    @Option(
        description = """
             Additional test arguments, each argument separated by comma (',') per suite.
             Each suite is separated by vertical slash ('|').
             Example: quarkus=-rf,:tika|quarkus-platform=-rf,:aws
            """
        , names =
        {
            "-ata"
            , "--additional-test-args"
        }
        , split = "\\|"
    )
    Map<String, String> additionalTestArgs = new HashMap<>();

    Options options;

    QuarkusTest(FileSystem fs, OperatingSystem os)
    {
        this.fs = fs;
        this.os = os;
    }

    @Override
    public void run()
    {
        options = Options.of(suites, alsoTest, additionalTestArgs);
        LOG.info(options);

        if (Objects.nonNull(fs) && Objects.nonNull(os))
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
            , Map<String, String> additionalTestArgs
        )
        {
            return new Options(
                suites
                , Git.URL.of(alsoTest)
                , Arguments.to(additionalTestArgs)
            );
        }
    }

    record Arguments(List<String>arguments)
    {
        static Map<String, Arguments> to(Map<String, String> arguments)
        {
            return arguments.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey
                        , Arguments::of
                    )
                );
        }

        private static Arguments of(Map.Entry<String, String> e)
        {
            return new Arguments(Arrays.asList(e.getValue().split(",")));
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

record DeprecatedMarker(Path path)
{
    private static final Logger LOG = LogManager.getLogger(DeprecatedMarker.class);

    static DeprecatedMarker download(String dirName, Root root)
    {
        return DeprecatedMarker.of("download.marker", dirName, root);
    }

    static DeprecatedMarker build(String dirName, Root root)
    {
        return DeprecatedMarker.of("build.marker", dirName, root);
    }

    private static DeprecatedMarker of(String markerName, String dirName, Root root)
    {
        return new DeprecatedMarker(
            root.path().resolve(
                Path.of(dirName, markerName)
            )
        );
    }

    static boolean skip(DeprecatedMarker marker)
    {
        final var path = marker.path;
        final var projectName = path.getParent().getFileName();
        LOG.info("Checking path {}", path);
        if (path.toFile().exists())
        {
            LOG.info("Skipping {} download, {} exists", projectName, path);
            return true;
        }

        return false;
    }

    static void touch(DeprecatedMarker marker)
    {
        DeprecatedOperatingSystem.touch(marker.path);
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
            .map(marker -> touch.apply(marker) ? marker.touch() : marker)
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
        return url -> Predicate.not(exists).test(url.marker());
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

class DeprecatedGit
{
    record URL(
        String organization
        , String name // TODO refactor to repoName or repository
        , String branch
        , String url
    )
    {
        static DeprecatedGit.URL of(URI uri)
        {
            final var path = Path.of(uri.getPath());

            final var organization = path.getName(0).toString();
            final var name = path.getName(1).toString();
            final var branch = path.getFileName().toString();
            final var url = uri.resolve("..").toString();

            return new URL(organization, name, branch, url);
        }

        static List<DeprecatedGit.URL> of(List<URI> uris)
        {
            return uris.stream()
                .map(DeprecatedGit.URL::of)
                .collect(Collectors.toList());
        }
    }

    static void download(List<DeprecatedGit.URL> urls, Root root)
    {
        urls.forEach(download(root));
    }

    private static Consumer<DeprecatedGit.URL> download(Root root)
    {
        return url ->
        {
            final var marker = DeprecatedMarker.download(url.name, root);
            if (DeprecatedMarker.skip(marker))
                return;

            DeprecatedOperatingSystem.exec()
                .compose(DeprecatedGit.deprecatedClone(url))
                .apply(root.path());

            DeprecatedMarker.touch(marker);
        };
    }

    static Function<Path, DeprecatedOperatingSystem.Command> deprecatedClone(DeprecatedGit.URL url)
    {
        return path ->
            new DeprecatedOperatingSystem.Command(
                Stream.of(
                    "git"
                    , "clone"
                    , "-b"
                    , url.branch
                    , "--depth"
                    , "10"
                    , url.url
                    , url.name
                )
                , path
                , Stream.empty()
            );
    }

    static Stream<String> clone(DeprecatedGit.URL url)
    {
        return Stream.of(
            "git"
            , "clone"
            , "-b"
            , url.branch
            , "--depth"
            , "10"
            , url.url
            , url.name
        );
    }
}

class Homes
{
    static Path java(Root root)
    {
        return root.path().resolve("java_home");
    }

    static Path graal(Root root)
    {
        return root.path().resolve("graal_home");
    }

    static class EnvVars
    {
        static DeprecatedOperatingSystem.EnvVar java(Root root)
        {
            return new DeprecatedOperatingSystem.EnvVar(
                "JAVA_HOME"
                , Homes.java(root).toString()
            );
        }

        static DeprecatedOperatingSystem.EnvVar deprecatedGraal(Root root)
        {
            return new DeprecatedOperatingSystem.EnvVar(
                "JAVA_HOME"
                , Homes.graal(root).toString()
            );
        }

        static OperatingSystem.EnvVar graal()
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , root -> root.resolve("graal_home")
            );
        }
    }
}

record Root(Path path)
{
    private static final Logger LOG = LogManager.getLogger(Root.class);

    static Root newSystemRoot()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        var today = date.format(formatter);
        LOG.info("Today is {}", today);

        var baseDir = Path.of(
            System.getProperty("user.home")
            , "target"
            , "quarkus-with-graal"
        );
        LOG.info("Base directory: {}", baseDir);

        final var path = baseDir.resolve(today);
        LOG.info("Root path: {}", path);

        return new Root(DeprecatedOperatingSystem.mkdirs(path));
    }
}

// Boundary value
record Marker(boolean touched, Path path)
{
    Marker touch()
    {
        return new Marker(true, path);
    }

    static Marker download(String dirName)
    {
        return new Marker(false, Path.of(dirName, "download.marker"));
    }
}

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
            , "target"
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

        boolean success = file.setLastModified(timestamp);
        if (!success)
        {
            throw new RuntimeException(
                String.format(
                    "Unable to update last modified time for: %s"
                    , path
                )
            );
        }

        LOG.debug("Touched {}", path);
        return success;
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

    record Task(Stream<String>task, Path directory, Stream<EnvVar>envVars) {}

    record MarkerTask(Task task, Marker marker) {}

    record EnvVar(String name, Function<Path, Path>value) {}
}

class DeprecatedOperatingSystem
{
    static final Logger LOG = LogManager.getLogger(DeprecatedOperatingSystem.class);

    public enum Type
    {
        WINDOWS, MACOSX, LINUX, OTHER
    }

    static void symlink(Path link, Path target)
    {
        try
        {
            if (Files.exists(link))
                Files.delete(link);

            Files.createSymbolicLink(link, target);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    static void touch(Path path)
    {
        long timestamp = System.currentTimeMillis();
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

        boolean success = file.setLastModified(timestamp);
        if (!success)
        {
            throw new RuntimeException(
                String.format(
                    "Unable to update last modified time for: %s"
                    , path
                )
            );
        }

        LOG.debug("Touched {}", path);
    }

    static Function<Path, Void> deleteRecursive()
    {
        return DeprecatedOperatingSystem::deleteRecursive;
    }

    private static Void deleteRecursive(Path path)
    {
        try
        {
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

            return null;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static Path mkdirs(Path path)
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

    public static Type type()
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

    static Function<Command, Void> exec()
    {
        return command ->
        {
            exec(command);
            return null;
        };
    }

    static void exec(Command command)
    {
        final var commandList = command.command
            .filter(Predicate.not(String::isEmpty))
            .collect(Collectors.toList());

        LOG.debug("Execute {} in {}", commandList, command.directory);
        try
        {
            var processBuilder = new ProcessBuilder(commandList)
                .directory(command.directory.toFile())
                .inheritIO();

            command.envVars.forEach(
                envVar -> processBuilder.environment()
                    .put(envVar.name, envVar.value)
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

    record Command(
        Stream<String>command
        , Path directory
        , Stream<EnvVar>envVars
    ) {}

    record EnvVar(String name, String value) {}

}

class QuarkusCheck
{
    static void check()
    {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectClass(CheckGit.class)
                , selectClass(CheckTest.class)
            )
            .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        listener.getSummary().printTo(new PrintWriter(System.out));
        listener.getSummary().printFailuresTo(new PrintWriter(System.err));
    }

    static class CheckGit
    {
        @Test
        void cloneDefault()
        {
            final var os = new RecordingOperatingSystem();
            final List<Git.URL> urls = Collections.emptyList();
            final var cloned = Git.clone(urls, m -> false, os::record, m -> false);
            os.assertSize(0);
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
            os.assertSize(1);
            os.assertMarkerTask(t -> assertThat(t.task().task().findFirst(), is(Optional.of("git"))));
            assertThat(cloned.size(), is(1));
            assertThat(cloned.get(0).touched(), is(true));
        }
    }

    static class CheckTest
    {
        @Test
        void cliAdditionalTestArgsOptions()
        {
            assertThat(
                execute("-ata", "a=b,:c|z=-y").additionalTestArgs
                , is(equalTo(Map.of("a", "b,:c", "z", "-y")))
            );
            assertThat(
                execute("--additional-test-args", "a=b,:c|z=-y").additionalTestArgs
                , is(equalTo(Map.of("a", "b,:c", "z", "-y")))
            );
        }

        @Test
        void cliAlsoTestOptions()
        {
            assertThat(
                execute("-at", "h://g/a/b,h://g/b/c").alsoTest
                , is(equalTo(List.of(URI.create("h://g/a/b"), URI.create("h://g/b/c"))))
            );
            assertThat(
                execute("--also-test", "h://g/w/x,h://g/y/z").alsoTest
                , is(equalTo(List.of(URI.create("h://g/w/x"), URI.create("h://g/y/z"))))
            );
        }

        @Test
        void cliSuiteOptions()
        {
            assertThat(
                execute("-s", "a,b").suites
                , is(equalTo(List.of("a", "b")))
            );
            assertThat(
                execute("--suites", "y,z").suites
                , is(equalTo(List.of("y", "z")))
            );
        }

        @Test
        void cliEmptyOptions()
        {
            final var command = execute();
            assertThat(command.suites, is(empty()));
            assertThat(command.alsoTest, is(empty()));
            assertThat(command.additionalTestArgs, is(anEmptyMap()));
        }

        private static QuarkusTest execute(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("test");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var quarkusTest = new QuarkusTest(null, null);
            new CommandLine(new quarkus())
                .addSubcommand("test", quarkusTest)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
            return quarkusTest;
        }

        @Test
        void testArguments()
        {
            final var options = QuarkusTest.Options.of(
                List.of("suite-a", "suite-b")
                , Collections.emptyList()
                , Map.of("suite-a", "p1,:p2", "suite-b", ":p3,-p4")
            );
            final var os = test(options);
            os.assertSize(2);
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
                , Collections.emptyMap()
            );
            final var os = test(options);
            os.assertSize(2);
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
                , Collections.emptyMap()
            );
            final var os = test(options);
            os.assertSize(1);
            os.assertTask(task ->
            {
                assertThat(task.task().findFirst(), is(Optional.of("mvn")));
                assertThat(task.directory(), is(Path.of("quarkus/integration-tests")));
            });
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

        void assertSize(int size)
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
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
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
        final var web = new Web(fs);

        final var graalBuild = GraalBuild.ofSystem(fs, os);
        final var graalGet = GraalGet.ofSystem(fs, os, web);
        final var mavenBuild = MavenBuild.ofSystem(fs, os);
        final var quarkusClean = QuarkusClean.ofSystem(fs);
        final var quarkusTest = MavenTest.ofSystem(fs, os);

        final var cli = Cli.newCli(
            graalBuild
            , graalGet
            , mavenBuild
            , quarkusClean
            , quarkusTest
        );
        int exitCode = cli.execute(args);
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

final class Cli
{
    static CommandLine newCli(Object... subcommands)
    {
        final var cmdline = new CommandLine(new quarkus());
        // Sub-commands need to be added first,
        // for converters and other options to have effect
        Arrays.asList(subcommands).forEach(cmdline::addSubcommand);
        return cmdline
            .registerConverter(Git.URL.class, Git.URL::of)
            .setCaseInsensitiveEnumValuesAllowed(true);
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
    name = "graal-get"
    , aliases = {"gg"}
    , description = "Get Graal."
    , mixinStandardHelpOptions = true
)
class GraalGet implements Runnable
{
    // TODO add namespace info
    static final Logger LOG = LogManager.getLogger(GraalGet.class);

    private final Consumer<Options> runner;

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

    private GraalGet(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static GraalGet ofSystem(FileSystem fs, OperatingSystem os, Web web)
    {
        return new GraalGet(new GraalGet.RunGet(fs, os, web));
    }

    static GraalGet of(Consumer<Options> runner)
    {
        return new GraalGet(runner);
    }

    @Override
    public void run()
    {
        LOG.info("Graal Get");
        final var options = new Options(url, Path.of("graal"));
        LOG.info(options);
        runner.accept(options);
    }

    final static class RunGet implements Consumer<Options>
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
        public void accept(Options options)
        {
            fs.mkdirs(options.graal);
            Graal.get(options, fs::exists, web::download, os::exec, fs::touch);
            Graal.link(options, fs::symlink);
        }
    }

    static final class Graal
    {
        static List<Marker> get(
            Options options
            , Predicate<Marker> exists
            , BiConsumer<URL, Path> download
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var downloadMarker = Graal.download(options, exists, download);
            final var extractMarker = Graal.extract(
                options
                , downloadMarker.path()
                , exists
                , exec
                , touch
            );
            final var nativeImageMarker = Graal.downloadNativeImage(options, exists, exec, touch);
            return Arrays.asList(downloadMarker, extractMarker, nativeImageMarker);
        }

        private static Marker downloadNativeImage(
            Options options
            , Predicate<Marker> exists
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var orgName = Path.of(options.url.getPath()).getName(0);
            if (!orgName.equals(Path.of("graalvm")))
            {
                LOG.info("Not a graalvm distro, assume the native-image exists");
                return Marker.notApplicable();
            }

            final var path = Path.of("native-image.marker");
            final var task = toDownloadNative(path, exists);
            return doDownloadNative(task, exec, touch);
        }

        private static OperatingSystem.MarkerTask toDownloadNative(
            Path markerPath
            , Predicate<Marker> exists
        )
        {
            final var marker = Marker.of(markerPath).query(exists);
            if (marker.exists())
                return OperatingSystem.MarkerTask.noop(marker);

            return new OperatingSystem.MarkerTask(
                new OperatingSystem.Task(
                    Stream.of(
                        "./gu"
                        , "install"
                        , "native-image"
                    )
                    , Path.of("graal", "bin")
                    , Stream.empty()
                )
                , marker
            );
        }

        private static Marker doDownloadNative(
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

        private static Marker download(
            Options options
            , Predicate<Marker> exists
            , BiConsumer<URL, Path> download
        )
        {
            final var url = options.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var marker = Marker.ofFileName(fileName, directory).query(exists);
            if (marker.exists())
                return marker;

            final var path = directory.resolve(fileName);
            download.accept(url, path);
            // No touching to be done, the file is the marker
            return marker.touch(m -> true);
        }

        private static Marker extract(
            Options options
            , Path tar
            , Predicate<Marker> exists
            , Consumer<OperatingSystem.Task> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var task = toExtract(tar, options.graal, exists);
            return doExtract(task, exec, touch);
        }

        private static Marker doExtract(
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

        private static OperatingSystem.MarkerTask toExtract(
            Path tar
            , Path target
            , Predicate<Marker> exists
        )
        {
            final var marker = Marker.extract(target).query(exists);
            if (marker.exists())
                return OperatingSystem.MarkerTask.noop(marker);

            return new OperatingSystem.MarkerTask(
                new OperatingSystem.Task(
                    Stream.of(
                        "tar"
                        , "-xzvpf"
                        , tar.toString()
                        , "-C"
                        , target.toString()
                        , "--strip-components"
                        , "1"
                    )
                    , Path.of("")
                    , Stream.empty()
                )
                , marker
            );
        }

        public static void link(Options options, BiFunction<Path, Path, Link> symLink)
        {
            final var link = Homes.graal();
            symLink.apply(link, options.graal);
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
class GraalBuild implements Runnable
{
    // TODO add namespace info
    static final Logger LOG = LogManager.getLogger(GraalBuild.class);

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

    private GraalBuild(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static GraalBuild ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new GraalBuild(new GraalBuild.RunBuild(fs, os));
    }

    static GraalBuild of(Consumer<Options> runner)
    {
        return new GraalBuild(runner);
    }

    @Override
    public void run()
    {
        LOG.info("Build");
        final var options = Options.of(
            jdkTree
            , mxTree
            , graalTree
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
        }
    }

    record Options(
        Git.URL jdk
        , Git.URL mx
        , Git.URL graal
    )
    {
        static Options of(
            URI jdk
            , URI mx
            , URI graal
        )
        {
            return new Options(
                Git.URL.of(jdk)
                , Git.URL.of(mx)
                , Git.URL.of(graal)
            );
        }

        static List<Git.URL> urls(Options options)
        {
            final var urls = new ArrayList<Git.URL>();
            urls.add(options.jdk);
            urls.add(options.mx);
            urls.add(options.graal);
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
}

@Command(
    name = "maven-build"
    , aliases = {"mb"}
    , description = "Maven build."
    , mixinStandardHelpOptions = true
)
class MavenBuild implements Runnable
{
    // TODO add namespace info
    static final Logger LOG = LogManager.getLogger(MavenBuild.class);

    // TODO make it not stating (can't use Stream because of unit tests), convert into defaults record instead
    private static final Map<String, List<String>> EXTRA_BUILD_ARGS = Map.of(
        "camel-quarkus", List.of("-Dquarkus.version=999-SNAPSHOT")
    );

    private final Consumer<Options> runner;

    @Option(
        description = "Source tree URL"
        , names =
        {
            "-t"
            , "--tree"
        }
        , required = true
    )
    Git.URL tree;

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

    private MavenBuild(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static MavenBuild ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new MavenBuild(new MavenBuild.RunBuild(fs, os));
    }

    static MavenBuild of(Consumer<MavenBuild.Options> runner)
    {
        return new MavenBuild(runner);
    }

    @Override
    public void run()
    {
        LOG.info("Build");
        final var options = new Options(tree, additionalBuildArgs);
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
            Git.clone(singletonList(options.tree), fs::exists, os::exec, fs::touch);
            Maven.build(options, fs::exists, os::exec, fs::touch);
        }
    }

    record Options(Git.URL tree, List<String> additionalBuildArgs)
    {
        Path project()
        {
            return Path.of(tree.name());
        }
    }

    static class Maven
    {
        static Marker build(
            Options options
            , Predicate<Marker> exists
            , Function<OperatingSystem.MarkerTask, Marker> exec
            , Function<Marker, Boolean> touch
        )
        {
            final var task = Maven.toBuild(options, exists);
            return Maven.doBuild(task, exec, touch);
        }

        private static OperatingSystem.MarkerTask toBuild(
            Options options
            , Predicate<Marker> exists
        )
        {
            final var marker = Marker.build(options.project()).query(exists);
            if (marker.exists())
                return OperatingSystem.MarkerTask.noop(marker);

            return Maven.buildTask(options, marker);
        }

        private static OperatingSystem.MarkerTask buildTask(Options options, Marker marker)
        {
            final var directory = marker.path().getParent();
            final var arguments = arguments(options, directory.toString());
            return new OperatingSystem.MarkerTask(
                new OperatingSystem.Task(
                    arguments
                    , directory
                    , Stream.of(Homes.EnvVars.graal())
                )
                , marker
            );
        }

        private static Stream<String> arguments(Options options, String directory)
        {
            LOG.info(
                "Compute task arguments for {} with additional build args {}"
                , directory
                , options.additionalBuildArgs
            );
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

        private static Marker doBuild(
            OperatingSystem.MarkerTask task
            , Function<OperatingSystem.MarkerTask, Marker> exec
            , Function<Marker, Boolean> touch
        )
        {
            if (OperatingSystem.Task.isNoop(task.task()))
                return task.marker();

            final var marker = exec.apply(task);
            return marker.touch(touch);
        }
    }
}

@Command(
    name = "maven-test"
    , aliases = {"mt"}
    , description = "Maven test."
    , mixinStandardHelpOptions = true
)
class MavenTest implements Runnable
{
    private static final Logger LOG = LogManager.getLogger(MavenTest.class);

    // TODO read camel-quarkus snapshot version from
    // TODO make it not stating (can't use Stream because of unit tests), convert into defaults record instead
    private static final Map<Path, List<String>> EXTRA_TEST_ARGS = Map.of(
        Path.of("quarkus-platform")
        , List.of(
            "-Dquarkus.version=999-SNAPSHOT"
            , "-Dcamel-quarkus.version=1.1.0-SNAPSHOT"
        )
    );

    private final Consumer<Options> runner;

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

    private MavenTest(Consumer<Options> runner)
    {
        this.runner = runner;
    }

    static MavenTest ofSystem(FileSystem fs, OperatingSystem os)
    {
        return new MavenTest(new RunTest(fs, os));
    }

    static MavenTest of(Consumer<Options> runner)
    {
        return new MavenTest(runner);
    }

    @Override
    public void run()
    {
        var options = new Options(suite, additionalTestArgs);
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
            Maven.test(options, os::exec);
        }
    }

    record Options(String suite, List<String> additionalTestArgs) {}

    static class Maven
    {
        static void test(Options options, Consumer<OperatingSystem.Task> exec)
        {
            final var task = Maven.toTest(options);
            Maven.doTest(task, exec);
        }

        private static void doTest(
            OperatingSystem.Task task
            , Consumer<OperatingSystem.Task> exec
        )
        {
            exec.accept(task);
        }

        private static OperatingSystem.Task toTest(Options options)
        {
            final var directory = Maven.suitePath(options.suite);
            final var arguments = arguments(options, directory);
            return new OperatingSystem.Task(
                arguments
                , directory
                , Stream.of(Homes.EnvVars.graal())
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

class Git
{
    record URL(
        String organization
        , String name // TODO refactor to repoName or repository
        , String branch
        , String url
    )
    {
        static Git.URL of(String uri)
        {
            return Git.URL.of(URI.create(uri));
        }

        static Git.URL of(URI uri)
        {
            final var path = Path.of(uri.getPath());

            final var organization = path.getName(0).toString();
            final var name = path.getName(1).toString();
            final var branch = extractBranch(path).toString();
            final var url = githubURL(organization, name);

            return new URL(organization, name, branch, url);
        }

        private static String githubURL(String organization, String repositoryName)
        {
            try
            {
                return new URI(
                    "https"
                    , "github.com"
                    , Path.of("/", organization, repositoryName).toString()
                    , null
                ).toString();
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
        return new Git.MarkerURL(url, Marker.clone(url.name()));
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

    private static final Marker NOT_APPLICABLE =
        Marker.of(Path.of("not.applicable"));

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
        return of(path.resolve("build.marker"));
    }

    static Marker clone(String dirName)
    {
        return of(Path.of(dirName, "clone.marker"));
    }

    static Marker extract(Path path)
    {
        return of(path.resolve("extract.marker"));
    }

    static Marker ofFileName(Path fileName, Path path)
    {
        final var markerFileName = String.format("%s.marker", fileName);
        return Marker.of(path.resolve(markerFileName));
    }

    static Marker of(Path path)
    {
        return new Marker(false, false, path);
    }

    static Marker notApplicable()
    {
        return NOT_APPLICABLE;
    }

    static boolean isNotApplicable(Marker marker)
    {
        return marker == NOT_APPLICABLE;
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
            final var expectedSize = contentLength(url);
            final var urlChannel = new DownloadProgressChannel(
                Channels.newChannel(url.openStream())
                , expectedSize
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

    private int contentLength(URL url)
    {
        try
        {
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            return connection.getContentLength();
        }
        catch (Exception ignored)
        {
            return -1;
        }
    }

    private static final class DownloadProgressChannel implements ReadableByteChannel
    {
        final ReadableByteChannel channel;
        final long expectedSize;

        long bytesCount;
        double progress;

        private DownloadProgressChannel(
            ReadableByteChannel channel
            , long expectedSize
        )
        {
            this.channel = channel;
            this.expectedSize = expectedSize;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
            int bytesRead;

            bytesRead = channel.read(dst);
            if (bytesRead > 0)
            {
                bytesCount += bytesRead;
                if (expectedSize > 0)
                {
                    progress = (double) bytesCount * 100 / (double) expectedSize;
                    LOG.info(
                        "Download progress {} received, {}"
                        , humanReadableByteCountBin(bytesCount)
                        , String.format("%.02f%%", progress)
                    );
                }
                else
                {
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
                , selectClass(CheckBuild.class)
                , selectClass(CheckTest.class)
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
            final var url = Git.URL.of(URI.create("https://github.com/openjdk/jdk11u-dev/tree/master"));
            assertThat(url.organization(), is("openjdk"));
            assertThat(url.name(), is("jdk11u-dev"));
            assertThat(url.branch(), is("master"));
            assertThat(url.url(), is("https://github.com/openjdk/jdk11u-dev"));
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
                    Marker.clone("repo-a"), true
                    , Marker.clone("repo-b"), false
                )
            );
            final var os = new RecordingOperatingSystem();
            final List<Git.URL> urls = Git.URL.of(
                List.of(
                    URI.create("h://g/a/repo-a/tree/master")
                    , URI.create("h:/g/b/repo-b/tree/branch")
                )
            );
            final var cloned = Git.clone(urls, fs::exists, os::record, fs::touch);
            os.assertNumberOfTasks(1);
            os.assertMarkerTask(t -> assertThat(t.task().task().findFirst(), is(Optional.of("git"))));
            assertThat(cloned.size(), is(1));
            assertThat(cloned.get(0).touched(), is(true));
        }

        @Test
        void branchWithPath()
        {
            final var url = Git.URL.of(URI.create("https://github.com/olpaw/graal/tree/paw/2367"));
            assertThat(url.organization(), is("olpaw"));
            assertThat(url.name(), is("graal"));
            assertThat(url.branch(), is("paw/2367"));
            assertThat(url.url(), is("https://github.com/olpaw/graal"));
        }
    }

    @ExtendWith(LoggingExtension.class)
    static class CheckGraalBuild
    {
        @Test
        void javaLabsJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = new InMemoryFileSystem(
                Map.of(
                    Marker.clone("labs-openjdk-11"), false
                )
            );
            final var options = cli();

            final var marker = GraalBuild.Java.build(
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

        @Test
        void javaOpenJDK()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.empty();
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var marker = GraalBuild.Java.build(
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
            final var fs = InMemoryFileSystem.ofExists(
                Marker.build(Path.of("jdk11u-dev"))
            );
            final var options = cli(
                "--jdk-tree",
                "https://github.com/openjdk/jdk11u-dev/tree/master"
            );

            final var marker = GraalBuild.Java.build(
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
            final var marker = GraalBuild.Graal.build(
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
            final var fs = InMemoryFileSystem.ofExists(
                Marker.build(Path.of("graal"))
            );
            final var options = cli();
            final var marker = GraalBuild.Graal.build(
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
            final var options = cli();
            final var linked = GraalBuild.Java.link(
                options
                , Link::new
            );
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
            final var linked = GraalBuild.Java.link(
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

        private static GraalBuild.Options cli(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("graal-build");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder<GraalBuild.Options>();
            final var command = GraalBuild.of(recorder);
            final var exitCode = Cli.newCli(command).execute(args);
            assertThat(exitCode, is(0));
            return recorder.options;
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
            assertThat(markers.size(), is(3));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(true));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(true));
            assertThat(Marker.isNotApplicable(markers.get(2)), is(true));
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
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(
                Marker.of(Path.of("downloads", "archive.tar.gz.marker"))
                , Marker.extract(Path.of("graal"))
            );
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
            os.assertNumberOfTasks(0);
            assertThat(markers.size(), is(3));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(false));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(false));
            assertThat(Marker.isNotApplicable(markers.get(2)), is(true));
        }

        @Test
        void skipOnlyDownload()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(
                Marker.of(Path.of("downloads", "archive.tar.gz.marker"))
            );
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
            os.assertNumberOfTasks(1);
            assertThat(markers.size(), is(3));
            final var downloadMarker = markers.get(0);
            assertThat(downloadMarker.exists(), is(true));
            assertThat(downloadMarker.touched(), is(false));
            final var extractMarker = markers.get(1);
            assertThat(extractMarker.exists(), is(true));
            assertThat(extractMarker.touched(), is(true));
            assertThat(Marker.isNotApplicable(markers.get(2)), is(true));
        }

        @Test
        void missingURL()
        {
            final var result = cliError();
            assertThat(result, is(2));
        }

        private static int cliError()
        {
            final List<String> list = new ArrayList<>();
            list.add("graal-get");
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder<GraalGet.Options>();
            final var command = GraalGet.of(recorder);
            return Cli.newCli(command)
                .setParameterExceptionHandler(
                    (ex, cmdArgs) ->
                        ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput()
                )
                .execute(args);
        }

        private static GraalGet.Options cli(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("graal-get");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder<GraalGet.Options>();
            final var command = GraalGet.of(recorder);
            final var exitCode = Cli.newCli(command).execute(args);
            assertThat(exitCode, is(0));
            return recorder.options;
        }

        static void downloadNoop(URL url, Path path) {}
    }

    @ExtendWith(LoggingExtension.class)
    static class CheckBuild
    {
        @Test
        void quarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = new InMemoryFileSystem(Collections.emptyMap());
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var marker = MavenBuild.Maven.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertNumberOfTasks(1);
            os.assertMarkerTask(task ->
            {
                assertThat(task.task().directory(), is(Path.of("quarkus")));
                assertThat(task.task().task().findFirst(), is(Optional.of("mvn")));
            });
        }

        @Test
        void skipQuarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = InMemoryFileSystem.ofExists(
                Marker.build(Path.of("quarkus"))
            );
            final var options = cli(
                "-t"
                , "https://github.com/quarkusio/quarkus/tree/master"
            );
            final var marker = MavenBuild.Maven.build(
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
        void camelQuarkus()
        {
            final var os = new RecordingOperatingSystem();
            final var fs = new InMemoryFileSystem(Collections.emptyMap());
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel-quarkus/tree/master"
            );
            final var marker = MavenBuild.Maven.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertMarkerTask(task ->
            {
                assertThat(task.task().directory(), is(Path.of("camel-quarkus")));
                final var arguments = task.task().task().collect(Collectors.toList());
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
            final var fs = new InMemoryFileSystem(Collections.emptyMap());
            final var options = cli(
                "-t"
                , "https://github.com/apache/camel/tree/master"
                , "-aba"
                , "-Pfastinstall,-Pdoesnotexist"
            );
            final var marker = MavenBuild.Maven.build(
                options
                , fs::exists
                , os::record
                , m -> true
            );
            assertThat(marker.exists(), is(true));
            assertThat(marker.touched(), is(true));
            os.assertMarkerTask(task ->
            {
                assertThat(task.task().directory(), is(Path.of("camel")));
                final var arguments = task.task().task().collect(Collectors.toList());
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

        private static MavenBuild.Options cli(String... extra)
        {
            final List<String> list = new ArrayList<>();
            list.add("maven-build");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder<MavenBuild.Options>();
            final var command = MavenBuild.of(recorder);
            final var exitCode = Cli.newCli(command).execute(args);
            assertThat(exitCode, is(0));
            return recorder.options;
        }
    }

    static class CheckTest
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
                    t.task().collect(Collectors.joining(" "))
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
            os.assertAllTasks(t -> assertThat(t.task().findFirst(), is(Optional.of("mvn"))));
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
                assertThat(task.task().findFirst(), is(Optional.of("mvn")));
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
                final var arguments = task.task().collect(Collectors.toList());
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
            final List<String> list = new ArrayList<>();
            list.add("maven-test");
            list.addAll(Arrays.asList(extra));
            final String[] args = new String[list.size()];
            list.toArray(args);

            final var recorder = new OptionsRecorder<MavenTest.Options>();
            final var command = MavenTest.of(recorder);
            final var exitCode = Cli.newCli(command).execute(args);
            assertThat(exitCode, is(0));
            return recorder.options;
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
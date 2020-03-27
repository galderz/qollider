//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.2.0
//DEPS org.apache.logging.log4j:log4j-core:2.13.0

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
    subcommands = {
        QuarkusClean.class
        , QuarkusBuild.class
        , QuarkusTest.class
    }
    , synopsisSubcommandLabel = "COMMAND"
)
public class quarkus implements Runnable
{
    @Spec
    CommandSpec spec;

    public static void main(String[] args)
    {
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.DEBUG);

        int exitCode = new CommandLine(new quarkus())
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
)
class QuarkusClean implements Runnable
{
    static final Logger LOG = LogManager.getLogger(QuarkusClean.class);

    @Override
    public void run()
    {
        LOG.info("Clean!");
        clean(Root.newSystemRoot());
    }

    static void clean(Root root)
    {
        OperatingSystem.deleteRecursive()
            .compose(Root::path)
            .apply(root);
    }
}

@Command(
    name = "build"
    , aliases = {"b"}
    , description = "Build quarkus."
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
        description = "Additional projects to build. Separated by comma(,) character."
        , names =
        {
            "-ab"
            , "--also-build"
        }
        , split = ","
    )
    List<URI> alsoBuild = new ArrayList<>();

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
            Git.URL.of(jdkTree)
            , Git.URL.of(mxTree)
            , Git.URL.of(graalTree)
            , Git.URL.of(alsoBuild)
            , Git.URL.of(quarkusTree)
        );
        LOG.info(options);

        final var urls = Options.urls(options);
        Git.download(urls, root);

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
        Git.URL jdk
        , Git.URL mx
        , Git.URL graal
        , List<Git.URL>alsoBuilds
        , Git.URL quarkus
    )
    {
        static List<Git.URL> urls(Options options)
        {
            final var merged = new ArrayList<Git.URL>();
            merged.add(options.jdk);
            merged.add(options.mx);
            merged.add(options.graal);
            merged.addAll(options.alsoBuilds);
            merged.add(options.quarkus);
            return merged;
        }
    }

    private record Java(Git.URL url, Path bootJdk)
    {
        static Java newSystemJava(Git.URL url)
        {
            var bootJDK = Path.of(System.getenv("BOOT_JDK_HOME"));
            return new Java(url, bootJDK);
        }

        static void build(Java java, Root root)
        {
            final var marker = Marker.build(java.url.name(), root);
            if (Marker.skip(marker))
                return;

            final var javaType = type(java.url);

            final var steps =
                switch (javaType)
                    {
                        case OPENJDK -> Java.OpenJDK.buildSteps(java, root);
                        case LABSJDK -> Java.LabsJDK.buildSteps(java, root);
                    };

            steps.forEach(OperatingSystem::exec);

            Marker.touch(marker);
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
            OperatingSystem.symlink(link, target);
        }

        static Path root(Java java, Root root)
        {
            return root.path().resolve(
                Path.of(java.url.name())
            );
        }

        static OperatingSystem.EnvVar bootJdkEnvVar(Java java)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , java.bootJdk.toString()
            );
        }

        private static final class OpenJDK
        {
            static Stream<OperatingSystem.Command> buildSteps(Java java, Root root)
            {
                return Stream.of(
                    configureSh(java, root)
                    , makeGraalJDK(java, root)
                );
            }

            static Path javaHome(Java java, Root root)
            {
                final var os = OperatingSystem.type().toString().toLowerCase();
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

            private static OperatingSystem.Command configureSh(Java java, Root root)
            {
                return new OperatingSystem.Command(
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

            private static OperatingSystem.Command makeGraalJDK(Java java, Root root)
            {
                return new OperatingSystem.Command(
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
            static Stream<OperatingSystem.Command> buildSteps(Java java, Root root)
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

            private static OperatingSystem.Command buildJDK(Java java, Root root)
            {
                return new OperatingSystem.Command(
                    Stream.of(
                        "python"
                        , "build_labsjdk.py"
                    )
                    , Java.root(java, root)
                    , Stream.of(Java.bootJdkEnvVar(java))
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

    private record Graal(Git.URL graalURL, Git.URL mxURL)
    {
        static Graal of(Options options)
        {
            final var graalURL = options.graal;
            final var mxURL = options.mx;
            return new Graal(graalURL, mxURL);
        }

        static void build(Graal graal, Root root)
        {
            final var marker = Marker.build(graal.graalURL.name(), root);
            if (Marker.skip(marker))
                return;

            OperatingSystem.exec()
                .compose(Graal.mxbuild(graal))
                .apply(root);

            Marker.touch(marker);
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
            OperatingSystem.symlink(link, target);
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

        private static Function<Root, OperatingSystem.Command> mxbuild(Graal graal)
        {
            return root ->
                new OperatingSystem.Command(
                    Stream.of(
                        Graal.mx(graal, root).toString()
                        , "build"
                    )
                    , Graal.svm(graal, root)
                    , Stream.of(Homes.EnvVars.java(root))
                );
        }
    }

    private record Maven(List<Git.URL>projects)
    {
        static Maven of(Options options)
        {
            final var projects = new ArrayList<>(options.alsoBuilds);
            projects.add(options.quarkus);
            return new Maven(projects);
        }

        static void build(Maven maven, Root root)
        {
            maven.projects
                .forEach(Maven.build(root));
        }

        static Consumer<Git.URL> build(Root root)
        {
            return url ->
                OperatingSystem.exec()
                    .compose(Maven.mvnInstall(url))
                    .apply(root);
        }

        private static Function<Root, OperatingSystem.Command> mvnInstall(Git.URL url)
        {
            return root ->
                new OperatingSystem.Command(
                    Stream.of(
                        "mvn" // ?
                        , "install"
                        , "-DskipTests"
                        , "-Dformat.skip"
                    )
                    , root.path().resolve(url.name())
                    , Stream.of(Homes.EnvVars.graal(root))
                );
        }
    }
}

@Command(
    name = "test"
    , aliases = {"t"}
    , description = "Test quarkus."
)
class QuarkusTest implements Runnable
{
    private static final Logger LOG = LogManager.getLogger(QuarkusTest.class);

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
        , split = ","
    )
    Map<String, String> additionalTestArgs = new HashMap<>();

    @Override
    public void run()
    {
        LOG.info("Test");
        final var root = Root.newSystemRoot();
        final var options = new Options(
            suites
            , Git.URL.of(alsoTest)
            , Arguments.to(additionalTestArgs)
        );
        LOG.info(options);

        Git.download(options.alsoTest, root);

        final var maven = Maven.of(options);
        Maven.test(maven, root);
    }

    record Options(
        List<String>suites
        , List<Git.URL>alsoTest
        , Map<String, Arguments>testArgs
    ) {}

    record Arguments(List<String>arguments)
    {
        static Map<String, Arguments> to(Map<String, String> arguments)
        {
            return arguments.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getValue
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

        static void test(Maven maven, Root root)
        {
            maven.suites.forEach(
                Maven.mvnTest(maven, root)
            );
        }

        private static Consumer<String> mvnTest(Maven maven, Root root)
        {
            return suite ->
            {
                final var args = maven.testArgs.get(suite);

                OperatingSystem.exec()
                    .compose(Maven.mvnTest(suite, args))
                    .apply(root);
            };
        }

        private static Function<Root, OperatingSystem.Command> mvnTest(String suite, Arguments args)
        {
            return root ->
                new OperatingSystem.Command(
                    Stream.concat(
                        Stream.of(
                            "mvn" // ?
                            , "install"
                            , "-Dnative"
                            , "-Dformat.skip"
                        )
                        , args.arguments.stream()
                    )
                    , root.path().resolve(suite)
                    , Stream.of(Homes.EnvVars.graal(root))
                );
        }
    }
}

//    @Override
//    public void run()
//    {
//        LOG.info("Test the combo!");
//        final var options = new Options(
//            Git.URL.of(quarkusTree)
//            , additionalTestArgs
//            , Git.URL.of(graalTree)
//            , testSuites
//            , Java.Vendor.of(jdkTree)
//            , Git.URL.of(alsoBuild)
//        );
//        final var paths = LocalPaths.newSystemPaths(options);
//        LOG.info(options);
//        Sequential.test(options, paths);
//    }
//}
//
//record Options(
//    Git.URL quarkus
//    , List<String>additionalTestArgs
//    , Git.URL graal
//    , TestSuite[]testSuites
//    , Java.Vendor jdk
//    , List<Git.URL>alsoBuilds
//) {}
//
//class Sequential
//{
//    static void test(Options options, LocalPaths paths)
//    {
//        Java.downloadJDK(options, paths);
//        Java.buildJDK(options, paths);
//
//        Graal.installMx(paths);
//        Graal.downloadGraal(options, paths);
//        JBoss.downloadProjects(options, paths);
//        JBoss.downloadQuarkus(options, paths);
//        JBoss.downloadQuarkusPlatform(paths);
//
//        // Build steps
//        Graal.buildGraal(paths);
//        JBoss.buildProjects(options, paths);
//        JBoss.buildQuarkus(paths);
//
//        // Test steps
//        JBoss.test(options, paths);
//    }
//}
//
//class JBoss
//{
//    static final Logger LOG = LogManager.getLogger(Java.class);
//
//    static void test(Options options, LocalPaths paths)
//    {
//        for (TestSuite suite : options.testSuites())
//        {
//            switch (suite)
//            {
//                case QUARKUS -> JBoss.testQuarkus(options, paths);
//                case PLATFORM -> JBoss.testQuarkusPlatform(options, paths);
//            }
//        }
//    }
//
//    static void testQuarkus(Options options, LocalPaths paths)
//    {
//        if (QuarkusPaths.lastTestJar(paths).toFile().exists())
//        {
//            LOG.info("Skipping Quarkus test");
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(JBoss::mvnTest)
//            .compose(Suite.of(options, paths))
//            .compose(QuarkusPaths.resolve(paths))
//            .apply("integration-tests");
//    }
//
//    static void testQuarkusPlatform(Options options, LocalPaths paths)
//    {
////        if (LocalPaths.quarkusLastTestJar(paths).toFile().exists())
////        {
////            LOG.info("Skipping Quarkus test");
////            return;
////        }
//
//        OperatingSystem.exec()
//            .compose(JBoss::mvnTest)
//            .compose(Suite.of(options, paths))
//            .compose(QuarkusPlatformPaths::root)
//            .apply(paths);
//    }
//
//    static void downloadProjects(Options options, LocalPaths paths)
//    {
//        options.alsoBuilds()
//            .forEach(JBoss.downloadProject(paths));
//    }
//
//    static Consumer<Git.URL> downloadProject(LocalPaths paths)
//    {
//        return url ->
//        {
//            final var marker = ProjectPaths.pomXml(url, paths);
//            LOG.info("Checking path {}", marker);
//            if (marker.toFile().exists())
//            {
//                LOG.info("Skipping {} download, {} exists", url.name(), marker);
//                return;
//            }
//
//            OperatingSystem.exec()
//                .compose(Git.clone(url))
//                .apply(paths.root());
//        };
//    }
//
//    static void downloadQuarkus(Options options, LocalPaths paths)
//    {
//        final var marker = QuarkusPaths.pomXml(paths);
//        LOG.info("Checking Quarkus path {}", marker);
//        if (marker.toFile().exists())
//        {
//            LOG.info("Skipping Quarkus download, {} exists", marker);
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(Git.clone(options.quarkus()))
//            .apply(paths.root());
//    }
//
//    static void downloadQuarkusPlatform(LocalPaths paths)
//    {
//        if (QuarkusPlatformPaths.pomXml(paths).toFile().exists())
//        {
//            LOG.info("Skipping Quarkus Platform download");
//            return;
//        }
//
//        final var tree = Git.URL.of(
//            "https://github.com/quarkusio/quarkus-platform/tree/master"
//        );
//        OperatingSystem.exec()
//            .compose(Git.clone(tree))
//            .apply(paths.root());
//    }
//
//    static void buildProjects(Options options, LocalPaths paths)
//    {
//        options.alsoBuilds()
//            .forEach(JBoss.buildProject(paths));
//    }
//
//    static Consumer<Git.URL> buildProject(LocalPaths paths)
//    {
//        return url ->
//            OperatingSystem.exec()
//                .compose(JBoss.mvnInstallProject(url))
//                .apply(paths);
//    }
//
//    private static Function<LocalPaths, OperatingSystem.Command> mvnInstallProject(Git.URL url)
//    {
//        return paths ->
//            new OperatingSystem.Command(
//                Stream.of(
//                    "mvn" // ?
//                    , "install"
//                    , "-DskipTests"
//                    , "-Dformat.skip"
//                )
//                , ProjectPaths.root(url, paths)
//                , Stream.of(LocalEnvs.Graal.graalJavaHome(paths))
//            );
//    }
//
//    static void buildQuarkus(LocalPaths paths)
//    {
//        if (QuarkusPaths.lastInstallJar(paths).toFile().exists())
//        {
//            LOG.info("Skipping Quarkus build");
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(JBoss::mvnInstall)
//            .apply(paths);
//    }
//
//    private static OperatingSystem.Command mvnInstall(LocalPaths paths)
//    {
//        return new OperatingSystem.Command(
//            Stream.of(
//                "mvn" // ?
//                , "install"
//                , "-DskipTests"
//                , "-Dformat.skip"
//            )
//            , QuarkusPaths.root(paths)
//            , Stream.of(LocalEnvs.Graal.graalJavaHome(paths))
//        );
//    }
//
//    private static OperatingSystem.Command mvnTest(Suite suite)
//    {
//        return new OperatingSystem.Command(
//            Stream.concat(
//                Stream.of(
//                    "mvn" // ?
//                    , "install"
//                    , "-Dnative"
//                    , "-Dformat.skip"
//                )
//                , suite.additionalTestArgs.stream()
//            )
//            , suite.root()
//            , Stream.of(suite.javaHome())
//        );
//    }
//
//    record Suite(
//        Path root
//        , List<String>additionalTestArgs
//        , OperatingSystem.EnvVar javaHome
//    )
//    {
//        static Function<Path, Suite> of(Options options, LocalPaths paths)
//        {
//            return root ->
//                new Suite(
//                    root
//                    , options.additionalTestArgs()
//                    , LocalEnvs.Graal.graalJavaHome(paths)
//                );
//        }
//    }
//}
//
//class Graal
//{
//    static final Logger LOG = LogManager.getLogger(Java.class);
//
//    static void buildGraal(LocalPaths paths)
//    {
//        if (GraalPaths.nativeImage(paths).toFile().exists())
//        {
//            LOG.info("Skipping Graal build");
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(Graal::mxbuild)
//            .apply(paths);
//    }
//
//    private static OperatingSystem.Command mxbuild(LocalPaths paths)
//    {
//        return new OperatingSystem.Command(
//            Stream.of(
//                MxPaths.mx(paths).toString()
//                , "build"
//            )
//            , GraalPaths.svm(paths)
//            , Stream.of(LocalEnvs.Java.javaHome(paths))
//        );
//    }
//
//    static void downloadGraal(Options options, LocalPaths paths)
//    {
//        if (GraalPaths.svm(paths).toFile().exists())
//        {
//            LOG.info("Skipping Graal download");
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(Git.clone(options.graal()))
//            .apply(paths.root());
//    }
//
//    static void installMx(LocalPaths paths)
//    {
//        if (MxPaths.mx(paths).toFile().exists())
//        {
//            LOG.info("Skipping Mx install");
//            return;
//        }
//
//        var tree = Git.URL.of("https://github.com/graalvm/mx/tree/master");
//        OperatingSystem.exec()
//            .compose(Git.clone(tree))
//            .apply(paths.root());
//    }
//}

record Marker(Path path)
{
    private static final Logger LOG = LogManager.getLogger(Marker.class);

    static Marker download(String dirName, Root root)
    {
        return Marker.of("download.marker", dirName, root);
    }

    static Marker build(String dirName, Root root)
    {
        return Marker.of("build.marker", dirName, root);
    }

    private static Marker of(String markerName, String dirName, Root root)
    {
        return new Marker(
            root.path().resolve(
                Path.of(dirName, markerName)
            )
        );
    }

    static boolean skip(Marker marker)
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

    static void touch(Marker marker)
    {
        OperatingSystem.touch(marker.path);
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

//        static Git.URL of(String spec)
//        {
//            return of(URIs.of(spec));
//        }

        static List<Git.URL> of(List<URI> uris)
        {
            return uris.stream()
                .map(Git.URL::of)
                .collect(Collectors.toList());
        }
    }

    static void download(List<Git.URL> urls, Root root)
    {
        urls.forEach(download(root));
    }

    private static Consumer<Git.URL> download(Root root)
    {
        return url ->
        {
            final var marker = Marker.download(url.name, root);
            if (Marker.skip(marker))
                return;

            OperatingSystem.exec()
                .compose(Git.clone(url))
                .apply(root.path());

            Marker.touch(marker);
        };
    }

    static Function<Path, OperatingSystem.Command> clone(Git.URL url)
    {
        return path ->
            new OperatingSystem.Command(
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
}

//class Java
//{
//    static final Logger LOG = LogManager.getLogger(Java.class);
//
//    record Vendor(Java.Vendor.Type type, Git.URL url, Path javaHome)
//    {
//        enum Type
//        {
//            OPENJDK, LABSJDK
//        }
//
//        static Vendor of(URI tree)
//        {
//            final var type = vendorType(tree);
//            final var javaHome = javaHome(type);
//            final var url = Git.URL.of(tree);
//            return new Vendor(type, url, javaHome);
//        }
//
//        private static Path javaHome(Java.Vendor.Type jdkType)
//        {
//            final var os = OperatingSystem.type().toString().toLowerCase();
//            final var arch = "x86_64";
//
//            return switch (jdkType)
//                {
//                    case OPENJDK -> Path.of(
//                        "build"
//                        , String.format("%s-%s-normal-server-release", os, arch)
//                        , "images"
//                        , "graal-builder-jdk"
//                    );
//                    case LABSJDK -> Path.of(
//                        "java_home"
//                    );
//                };
//        }
//
//        private static Java.Vendor.Type vendorType(URI jdkTree)
//        {
//            // TODO no longer need
//            final String provider = Path.of(jdkTree.getPath()).getName(0).toString();
//            return switch (provider)
//                {
//                    case "openjdk" -> Type.OPENJDK;
//                    case "graalvm" -> Type.LABSJDK;
//                    default -> throw new IllegalStateException(
//                        "Unexpected value: " + provider
//                    );
//                };
//        }
//    }
//
//    static void downloadJDK(Options options, LocalPaths paths)
//    {
//        if (JavaPaths.makefile(paths).toFile().exists())
//        {
//            LOG.info("Skipping JDK source repository download");
//            return;
//        }
//
//        OperatingSystem.exec()
//            .compose(Java.downloadJDKCommand(options))
//            .apply(paths);
//    }
//
//    static void buildJDK(Options options, LocalPaths paths)
//    {
//        if (JavaPaths.javaBin(paths).toFile().exists())
//        {
//            LOG.info("Skipping JDK install");
//            return;
//        }
//
//        final var steps =
//            switch (options.jdk().type)
//                {
//                    case OPENJDK -> OpenJDK.buildSteps(paths);
//                    case LABSJDK -> LabsJDK.buildSteps(paths);
//                };
//
//        steps.forEach(OperatingSystem::exec);
//    }
//
//    private static Function<LocalPaths, OperatingSystem.Command> downloadJDKCommand(Options options)
//    {
//        return paths ->
//        {
//            var repo = options.jdk().url;
//            return Git.clone("jdk", repo).apply(paths.root());
//        };
//    }
//
//    private static final class OpenJDK
//    {
//        static Stream<OperatingSystem.Command> buildSteps(LocalPaths paths)
//        {
//            return Stream.of(
//                configureSh(paths)
//                , makeGraalJDK(paths)
//            );
//        }
//
//        private static OperatingSystem.Command configureSh(LocalPaths paths)
//        {
//            return new OperatingSystem.Command(
//                Stream.of(
//                    "sh"
//                    , "configure"
//                    , "--disable-warnings-as-errors"
//                    , "--with-jvm-features=graal"
//                    , "--with-jvm-variants=server"
//                    , "--enable-aot=no"
//                    , String.format("--with-boot-jdk=%s", JavaPaths.bootJDK(paths).toString())
//                )
//                , JavaPaths.root(paths)
//                , Stream.empty()
//            );
//        }
//
//        private static OperatingSystem.Command makeGraalJDK(LocalPaths paths)
//        {
//            return new OperatingSystem.Command(
//                Stream.of(
//                    "make"
//                    , "graal-builder-image"
//                )
//                , JavaPaths.root(paths)
//                , Stream.empty()
//            );
//        }
//    }
//
//    private static final class LabsJDK
//    {
//        static Stream<OperatingSystem.Command> buildSteps(LocalPaths paths)
//        {
//            return Stream.of(buildJDK(paths));
//        }
//
//        private static OperatingSystem.Command buildJDK(LocalPaths paths)
//        {
//            return new OperatingSystem.Command(
//                Stream.of(
//                    "python"
//                    , "build_labsjdk.py"
//                )
//                , JavaPaths.root(paths)
//                , Stream.of(LocalEnvs.Java.jdkBootHome(paths))
//            );
//        }
//    }
//}

//class LocalEnvs
//{
//    static class Java
//    {
////        static OperatingSystem.EnvVar javaHome(LocalPaths paths)
////        {
////            return new OperatingSystem.EnvVar(
////                "JAVA_HOME"
////                , JavaPaths.javaHome(paths).toString()
////            );
////        }
//
////        static OperatingSystem.EnvVar jdkBootHome(LocalPaths paths)
////        {
////            return new OperatingSystem.EnvVar(
////                "JAVA_HOME"
////                , JavaPaths.bootJDK(paths).toString()
////            );
////        }
//    }
//
//    static class Graal
//    {
//    }
//}

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
        static OperatingSystem.EnvVar java(Root root)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , Homes.java(root).toString()
            );
        }

        static OperatingSystem.EnvVar graal(Root root)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , Homes.graal(root).toString()
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

        return new Root(OperatingSystem.mkdirs(path));
    }
}

//record LocalPaths(
//    Path root
//    , JavaPaths java
//    , MxPaths mx
//    , GraalPaths graal
//    , QuarkusPaths quarkus
//    , QuarkusPlatformPaths quarkusPlatform
//)
//{
//    static LocalPaths newSystemPaths(Options options)
//    {
//        var root = OperatingSystem.mkdirs(Root.path());
//
//        var bootJDK = Path.of(System.getenv("BOOT_JDK_HOME"));
//        var jdkRoot = root.resolve("jdk");
//        var javaHome = jdkRoot.resolve(options.jdk().javaHome());
//        var java = new JavaPaths(jdkRoot, bootJDK, javaHome);
//
//        var mx = new MxPaths(root.resolve("mx"));
//        var quarkus = new QuarkusPaths(root.resolve("quarkus"));
//        var graal = new GraalPaths(root.resolve("graal"));
//        var quarkusPlatform = new QuarkusPlatformPaths(root.resolve("quarkus-platform"));
//        return new LocalPaths(root, java, mx, graal, quarkus, quarkusPlatform);
//    }
//}
//
//final class JavaPaths
//{
//    private final Path root;
//    private final Path bootJDK;
//    private final Path javaHome;
//
//    JavaPaths(Path root, Path bootJDK, Path javaHome)
//    {
//        this.root = root;
//        this.bootJDK = bootJDK;
//        this.javaHome = javaHome;
//    }
//
//    static Path root(LocalPaths paths)
//    {
//        return paths.java().root;
//    }
//
//    static Path bootJDK(LocalPaths paths)
//    {
//        return paths.java().bootJDK;
//    }
//
//    static Path javaHome(LocalPaths paths)
//    {
//        return paths.java().javaHome;
//    }
//
//    static Path javaBin(LocalPaths paths)
//    {
//        return javaHome(paths).resolve(
//            Path.of(
//                "bin"
//                , "java"
//            )
//        );
//    }
//
//    static Path makefile(LocalPaths paths)
//    {
//        return paths.java().root.resolve("Makefile");
//    }
//}
//
//final class MxPaths
//{
//    private final Path root;
//
//    MxPaths(Path root)
//    {
//        this.root = root;
//    }
//
//    static Path root(LocalPaths paths)
//    {
//        return paths.mx().root;
//    }
//
//    static Path mx(LocalPaths paths)
//    {
//        return root(paths).resolve("mx");
//    }
//}
//
//final class GraalPaths
//{
//    final Path root;
//
//    GraalPaths(Path root)
//    {
//        this.root = root;
//    }
//
//    static Path root(LocalPaths paths)
//    {
//        return paths.graal().root;
//    }
//
//    static Path graalHome(LocalPaths paths)
//    {
//        return root(paths).resolve(
//            Path.of(
//                "sdk"
//                , "latest_graalvm_home"
//            )
//        );
//    }
//
//    static Path svm(LocalPaths paths)
//    {
//        return root(paths).resolve("substratevm");
//    }
//
//    static Path nativeImage(LocalPaths paths)
//    {
//        return graalHome(paths)
//            .resolve(
//                Path.of(
//                    "bin"
//                    , "native-image"
//                )
//            );
//    }
//}
//
//final class QuarkusPaths
//{
//    final Path root;
//
//    QuarkusPaths(Path root)
//    {
//        this.root = root;
//    }
//
//    static Path root(LocalPaths paths)
//    {
//        return paths.quarkus().root;
//    }
//
//    static Function<String, Path> resolve(LocalPaths paths)
//    {
//        return path -> root(paths).resolve(path);
//    }
//
//    static Path pomXml(LocalPaths paths)
//    {
//        return root(paths).resolve("pom.xml");
//    }
//
//    static Path lastInstallJar(LocalPaths paths)
//    {
//        return root(paths).resolve(
//            Path.of(
//                "docs"
//                , "target"
//                , "quarkus-documentation-999-SNAPSHOT.jar"
//            )
//        );
//    }
//
//    static Path lastTestJar(LocalPaths paths)
//    {
//        return root(paths).resolve(
//            Path.of(
//                "integration-tests"
//                , "boostrap-config"
//                , "application"
//                , "target"
//                , "quarkus-integration-test-bootstrap-config-application-999-SNAPSHOT.jar"
//            )
//        );
//    }
//}
//
//final class QuarkusPlatformPaths
//{
//    final Path root;
//
//    QuarkusPlatformPaths(Path root)
//    {
//        this.root = root;
//    }
//
//    static Path root(LocalPaths paths)
//    {
//        return paths.quarkusPlatform().root;
//    }
//
//    static Path pomXml(LocalPaths paths)
//    {
//        return root(paths).resolve("pom.xml");
//    }
//}
//
//final class ProjectPaths
//{
//    static Path root(Git.URL url, LocalPaths paths)
//    {
//        return paths.root().resolve(url.name());
//    }
//
//    static Path pomXml(Git.URL url, LocalPaths paths)
//    {
//        return root(url, paths).resolve("pom.xml");
//    }
//}
//
//enum TestSuite
//{
//    QUARKUS, PLATFORM
//}

class OperatingSystem
{
    static final Logger LOG = LogManager.getLogger(OperatingSystem.class);

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
        return OperatingSystem::deleteRecursive;
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

//class URIs
//{
//    static URI of(String spec)
//    {
//        try
//        {
//            return new URI(spec);
//        }
//        catch (URISyntaxException e)
//        {
//            throw new RuntimeException(e);
//        }
//    }
//}

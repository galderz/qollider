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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
    subcommands = {
        QuarkusTest.class
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
    name = "test"
    , aliases = {"t"}
    , description = "Test quarkus."
)
class QuarkusTest implements Runnable
{
    static final Logger LOG = LogManager.getLogger(QuarkusTest.class);

    @Option(
        description = "Clean files and directories"
        , names = "clean"
    )
    boolean clean;

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

    @Option(
        defaultValue = ""
        , description = "Resume reactor from specified project"
        , names =
        {
            "-rf"
            , "--resume-from"
        }
    )
    String resumeFrom;

    @Option(
        defaultValue = "quarkus,platform"
        , description = "Test suites to run. Valid values: ${COMPLETION-CANDIDATES}"
        , names =
        {
            "-ts"
            , "--test-suites"
        }
        , split = ","
    )
    TestSuite[] testSuites;

    @Override
    public void run()
    {
        LOG.info("Test the combo!");
        final var paths = LocalPaths.newSystemPaths();
        final var options = new Options(quarkusTree, resumeFrom, clean, testSuites);
        LOG.info("Options: {}", options);
        if (options.clean())
            Sequential.clean(paths);

        Sequential.test(options, paths);
    }
}

record Options(
    URI quarkusTree
    , String resumeFrom
    , boolean clean
    , TestSuite[]testSuites
) {}

class Sequential
{
    static void clean(LocalPaths paths)
    {
        Graal.cleanMx(paths);
        Graal.cleanGraal(paths);
        JBoss.cleanQuarkus(paths);
    }

    static void test(Options options, LocalPaths paths)
    {
        Java.downloadJDK(paths);
        Java.buildJDK(paths);

        Graal.installMx(paths);
        Graal.downloadGraal(paths);
        JBoss.downloadQuarkus(options, paths);
        JBoss.downloadQuarkusPlatform(paths);

        // Build steps
        Graal.buildGraal(paths);
        JBoss.buildQuarkus(paths);

        // Test steps
        JBoss.test(options, paths);
    }
}

class JBoss
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void test(Options options, LocalPaths paths)
    {
        for (TestSuite suite : options.testSuites())
        {
            switch (suite)
            {
                case QUARKUS:
                    JBoss.testQuarkus(options, paths);
                    break;
                case PLATFORM:
                    JBoss.testQuarkusPlatform(options, paths);
                    break;
            }
        }
    }

    static void testQuarkus(Options options, LocalPaths paths)
    {
        if (QuarkusPaths.lastTestJar(paths).toFile().exists())
        {
            LOG.info("Skipping Quarkus test");
            return;
        }

        OperatingSystem.exec()
            .compose(JBoss::mvnTest)
            .compose(Suite.of(options, paths))
            .compose(QuarkusPaths.resolve(paths))
            .apply("integration-tests");
    }

    static void testQuarkusPlatform(Options options, LocalPaths paths)
    {
//        if (LocalPaths.quarkusLastTestJar(paths).toFile().exists())
//        {
//            LOG.info("Skipping Quarkus test");
//            return;
//        }

        OperatingSystem.exec()
            .compose(JBoss::mvnTest)
            .compose(Suite.of(options, paths))
            .compose(QuarkusPlatformPaths::root)
            .apply(paths);
    }

    static void downloadQuarkus(Options options, LocalPaths paths)
    {
        final var marker = QuarkusPaths.pomXml(paths);
        LOG.info("Checking Quarkus marker {}", marker);
        if (marker.toFile().exists())
        {
            LOG.info("Skipping Quarkus download, {} exists", marker);
            return;
        }

        OperatingSystem.exec()
            .compose(Git.clone(options.quarkusTree()))
            .apply(paths.root());
    }

    static void downloadQuarkusPlatform(LocalPaths paths)
    {
        if (QuarkusPlatformPaths.pomXml(paths).toFile().exists())
        {
            LOG.info("Skipping Quarkus Platform download");
            return;
        }

        final var tree = URIs.of(
            "https://github.com/quarkusio/quarkus-platform/tree/master"
        );
        OperatingSystem.exec()
            .compose(Git.clone(tree))
            .apply(paths.root());
    }

    static void buildQuarkus(LocalPaths paths)
    {
        if (QuarkusPaths.lastInstallJar(paths).toFile().exists())
        {
            LOG.info("Skipping Quarkus build");
            return;
        }

        OperatingSystem.exec()
            .compose(JBoss::mvnInstall)
            .apply(paths);
    }

    private static OperatingSystem.Command mvnInstall(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "mvn" // ?
                , "install"
                , "-DskipTests"
                , "-Dno-format"
            )
            , QuarkusPaths.root(paths)
            , Stream.of(
            LocalEnvs.Graal.graalJavaHome(paths)
        )
        );
    }

    private static OperatingSystem.Command mvnTest(Suite suite)
    {
        Stream<String> resumeFrom = suite.resumeFrom().isEmpty()
            ? Stream.empty()
            : Stream.of("-rf", suite.resumeFrom());

        return new OperatingSystem.Command(
            Stream.concat(
                Stream.of(
                    "mvn" // ?
                    , "install"
                    , "-Dnative"
                    , "-Dno-format"
                )
                , resumeFrom
            )
            , suite.root()
            , Stream.of(suite.javaHome())
        );
    }

    public static void cleanQuarkus(LocalPaths paths)
    {
        OperatingSystem.deleteRecursive()
            .compose(QuarkusPaths::root)
            .apply(paths);
    }

    record Suite(
        Path root
        , String resumeFrom
        , OperatingSystem.EnvVar javaHome
    )
    {
        static Function<Path, Suite> of(Options options, LocalPaths paths)
        {
            return root ->
                new Suite(
                    root
                    , options.resumeFrom()
                    , LocalEnvs.Graal.graalJavaHome(paths)
                );
        }
    }
}

class Graal
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void buildGraal(LocalPaths paths)
    {
        if (GraalPaths.nativeImage(paths).toFile().exists())
        {
            LOG.info("Skipping Graal build");
            return;
        }

        OperatingSystem.exec()
            .compose(Graal::mxbuild)
            .apply(paths);
    }

    private static OperatingSystem.Command mxbuild(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                MxPaths.mx(paths).toString()
                , "build"
            )
            , GraalPaths.svm(paths)
            , Stream.of(LocalEnvs.Java.javaHome(paths))
        );
    }

    static void downloadGraal(LocalPaths paths)
    {
        if (GraalPaths.svm(paths).toFile().exists())
        {
            LOG.info("Skipping Graal download");
            return;
        }

        var tree = URIs.of("https://github.com/oracle/graal/tree/master");
        OperatingSystem.exec()
            .compose(Git.clone(tree))
            .apply(paths.root());
    }

    static void installMx(LocalPaths paths)
    {
        if (MxPaths.mx(paths).toFile().exists())
        {
            LOG.info("Skipping Mx install");
            return;
        }

        var tree = URIs.of("https://github.com/graalvm/mx/tree/master");
        OperatingSystem.exec()
            .compose(Git.clone(tree))
            .apply(paths.root());
    }

    public static void cleanMx(LocalPaths paths)
    {
        OperatingSystem.deleteRecursive()
            .compose(MxPaths::root)
            .apply(paths);
    }

    public static void cleanGraal(LocalPaths paths)
    {
        OperatingSystem.deleteRecursive()
            .compose(GraalPaths::root)
            .apply(paths);
    }
}

class Git
{
    record Repository(String directory, URI uri, String branch)
    {
        static Repository of(URI tree)
        {
            final var path = Path.of(tree.getPath());
            final var directory = path.getName(1).toString();
            return Repository.of(directory, path, tree);
        }

        static Repository of(String directory, URI tree)
        {
            final var path = Path.of(tree.getPath());
            return Repository.of(directory, path, tree);
        }

        private static Repository of(String directory, Path path, URI tree)
        {
            final var repo = tree.resolve("..");
            final var branch = path.getFileName().toString();
            return new Repository(directory, repo, branch);
        }
    }

    static Function<Path, OperatingSystem.Command> clone(URI tree)
    {
        return clone(Repository.of(tree));
    }

    static Function<Path, OperatingSystem.Command> clone(String directory, URI tree)
    {
        return clone(Repository.of(directory, tree));
    }

    private static Function<Path, OperatingSystem.Command> clone(Repository repo)
    {
        return path ->
            new OperatingSystem.Command(
                Stream.of(
                    "git"
                    , "clone"
                    , "-b"
                    , repo.branch()
                    , "--depth"
                    , "1"
                    , repo.uri().toString()
                    , repo.directory()
                )
                , path
                , Stream.empty()
            );
    }
}

class Java
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void downloadJDK(LocalPaths paths)
    {
        if (JavaPaths.makefile(paths).toFile().exists())
        {
            LOG.info("Skipping JDK source repository download");
            return;
        }

        OperatingSystem.exec()
            .compose(Java::downloadJDKCommand)
            .apply(paths);
    }

    static void buildJDK(LocalPaths paths)
    {
        if (JavaPaths.javaBin(paths).toFile().exists())
        {
            LOG.info("Skipping JDK install");
            return;
        }

        final var steps = Stream.of(
            Java.configureSh(paths)
            , Java.makeJDK(paths)
            , Java.makeGraalJDK(paths)
        );

        steps.forEach(OperatingSystem::exec);
    }

    private static OperatingSystem.Command configureSh(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "sh"
                , "configure"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                , "--enable-aot=no"
                , String.format("--with-boot-jdk=%s", JavaPaths.bootJDK(paths).toString())
            )
            , JavaPaths.root(paths)
            , Stream.empty()
        );
    }

    private static OperatingSystem.Command makeJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "make"
                , "images"
            )
            , JavaPaths.root(paths)
            , Stream.empty()
        );
    }

    private static OperatingSystem.Command makeGraalJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "make"
                , "graal-builder-image"
            )
            , JavaPaths.root(paths)
            , Stream.empty()
        );
    }

    private static OperatingSystem.Command downloadJDKCommand(LocalPaths paths)
    {
        var repo = URIs.of("https://github.com/openjdk/jdk11u-dev/tree/master");
        return Git.clone("jdk", repo).apply(paths.root());
    }

}

class LocalEnvs
{
    static class Java
    {
        static OperatingSystem.EnvVar javaHome(LocalPaths paths)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , JavaPaths.javaHome(paths).toString()
            );
        }
    }

    static class Graal
    {
        static OperatingSystem.EnvVar graalJavaHome(LocalPaths paths)
        {
            return new OperatingSystem.EnvVar(
                "JAVA_HOME"
                , GraalPaths.graalHome(paths).toString()
            );
        }
    }

}

record LocalPaths(
    Path root
    , JavaPaths java
    , MxPaths mx
    , GraalPaths graal
    , QuarkusPaths quarkus
    , QuarkusPlatformPaths quarkusPlatform
)
{
    static final Logger logger = LogManager.getLogger(LocalPaths.class);

    static LocalPaths newSystemPaths()
    {
        var root = OperatingSystem.mkdirs(rootPath());

        var bootJDK = Path.of(System.getenv("BOOT_JDK_HOME"));
        var java = new JavaPaths(root.resolve("jdk"), bootJDK);

        var mx = new MxPaths(root.resolve("mx"));
        var quarkus = new QuarkusPaths(root.resolve("quarkus"));
        var graal = new GraalPaths(root.resolve("graal"));
        var quarkusPlatform = new QuarkusPlatformPaths(root.resolve("quarkus-platform"));
        return new LocalPaths(root, java, mx, graal, quarkus, quarkusPlatform);
    }

    private static Path rootPath()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        var today = date.format(formatter);
        logger.info("Today is {}", today);

        var baseDir = Path.of(
            System.getProperty("user.home")
            , "target"
            , "quarkus-with-graal"
        );
        logger.info("Base directory: {}", baseDir);

        final var root = baseDir.resolve(today);
        logger.info("Root path: {}", root);

        return root;
    }
}

final class JavaPaths
{
    private final Path root;
    private final Path bootJDK;

    JavaPaths(Path root, Path bootJDK)
    {
        this.root = root;
        this.bootJDK = bootJDK;
    }

    static Path root(LocalPaths paths)
    {
        return paths.java().root;
    }

    static Path bootJDK(LocalPaths paths)
    {
        return paths.java().bootJDK;
    }

    static Path javaHome(LocalPaths paths)
    {
        final var osArchName =
            switch (OperatingSystem.type())
                {
                    case MAC_OS -> "macosx-x86_64";
                    case LINUX -> "linux-x86_64";
                    case WINDOWS -> "windows-x86_64";
                    case OTHER -> throw new RuntimeException("NYI");
                };

        return paths.java().root.resolve(
            Path.of(
                "build"
                , String.format("%s-normal-server-release", osArchName)
                , "images"
                , "graal-builder-jdk"
            )
        );
    }

    static Path javaBin(LocalPaths paths)
    {
        return javaHome(paths).resolve(
            Path.of(
                "bin"
                , "java"
            )
        );
    }

    static Path makefile(LocalPaths paths)
    {
        return paths.java().root.resolve("Makefile");
    }
}

final class MxPaths
{
    private final Path root;

    MxPaths(Path root)
    {
        this.root = root;
    }

    static Path root(LocalPaths paths)
    {
        return paths.mx().root;
    }

    static Path mx(LocalPaths paths)
    {
        return paths.mx().root.resolve("mx");
    }
}

final class GraalPaths
{
    final Path root;

    GraalPaths(Path root)
    {
        this.root = root;
    }

    static Path root(LocalPaths paths)
    {
        return paths.graal().root;
    }

    static Path graalHome(LocalPaths paths)
    {
        return root(paths).resolve(
            Path.of(
                "sdk"
                , "latest_graalvm_home"
            )
        );
    }

    static Path svm(LocalPaths paths)
    {
        return root(paths).resolve("substratevm");
    }

    static Path nativeImage(LocalPaths paths)
    {
        return graalHome(paths)
            .resolve(
                Path.of(
                    "bin"
                    , "native-image"
                )
            );
    }
}

final class QuarkusPaths
{
    final Path root;

    QuarkusPaths(Path root)
    {
        this.root = root;
    }

    static Path root(LocalPaths paths)
    {
        return paths.quarkus().root;
    }

    static Function<String, Path> resolve(LocalPaths paths)
    {
        return path -> root(paths).resolve(path);
    }

    static Path pomXml(LocalPaths paths)
    {
        return root(paths).resolve("pom.xml");
    }

    static Path lastInstallJar(LocalPaths paths)
    {
        return root(paths).resolve(
            Path.of(
                "docs"
                , "target"
                , "quarkus-documentation-999-SNAPSHOT.jar"
            )
        );
    }

    static Path lastTestJar(LocalPaths paths)
    {
        return root(paths).resolve(
            Path.of(
                "integration-tests"
                , "boostrap-config"
                , "application"
                , "target"
                , "quarkus-integration-test-bootstrap-config-application-999-SNAPSHOT.jar"
            )
        );
    }
}

final class QuarkusPlatformPaths
{
    final Path root;

    QuarkusPlatformPaths(Path root)
    {
        this.root = root;
    }

    static Path root(LocalPaths paths)
    {
        return paths.quarkusPlatform().root;
    }

    static Path pomXml(LocalPaths paths)
    {
        return root(paths).resolve("pom.xml");
    }
}

enum TestSuite
{
    QUARKUS, PLATFORM
}

class OperatingSystem
{
    static final Logger logger = LogManager.getLogger(OperatingSystem.class);

    public enum Type
    {
        WINDOWS, MAC_OS, LINUX, OTHER
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
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);

        if ((OS.contains("mac")) || (OS.contains("darwin")))
            return Type.MAC_OS;

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

        logger.debug("Execute {} in {}", commandList, command.directory);
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

    record Command(
        Stream<String>command
        , Path directory
        , Stream<EnvVar>envVars
    ) {}

    record EnvVar(String name, String value) {}

}

class URIs
{
    static URI of(String spec)
    {
        try
        {
            return new URI(spec);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }
}

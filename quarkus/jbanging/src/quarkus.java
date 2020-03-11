//usr/bin/env jbang "$0" "$@" ; exit $?
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

import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        int exitCode = new CommandLine(new quarkus()).execute(args);
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
        defaultValue = "https://github.com/quarkusio/quarkus/tree/master"
        , description = "Quarkus source tree URL"
        , names = {
            "-qt"
            , "--quarkus-tree"
        }
    )
    URL quarkusTree;

    @Override
    public void run()
    {
        LOG.info("Test the combo!");
        final var paths = LocalPaths.newSystemPaths();
        final var options = Options.of(quarkusTree);
        Sequential.test(options, paths);
    }
}

class Options
{
    final String quarkusRepo;
    final String quarkusBranch;

    private Options(String quarkusRepo, String quarkusBranch)
    {
        this.quarkusRepo = quarkusRepo;
        this.quarkusBranch = quarkusBranch;
    }

    static Options of(URL quarkusTree)
    {
        var urlPath = quarkusTree.getPath().split("/");
        var quarkusBranch = urlPath[urlPath.length - 1];
        var quarkusRepo = String.format(
            "%s://%s/%s/%s"
            , quarkusTree.getProtocol()
            , quarkusTree.getAuthority()
            , urlPath[1]
            , urlPath[2]
        );
        return new Options(quarkusRepo, quarkusBranch);
    }
}

class Sequential
{
    static void test(Options options, LocalPaths paths)
    {
        // Prepare steps
        Java.installJDK(paths);
        Graal.installMx(paths);
        Graal.downloadGraal(paths);
        JBoss.downloadQuarkus(options, paths);

        // Build steps
        Graal.buildGraal(paths);
        JBoss.buildQuarkus(paths);

        // Test steps
        JBoss.testQuarkus(paths);
    }
}

class JBoss
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void testQuarkus(LocalPaths paths)
    {
        if (LocalPaths.quarkusLastTestJar(paths).toFile().exists())
        {
            LOG.info("Skipping Quarkus test");
            return;
        }

        OperatingSystem.exec()
            .compose(JBoss::mvnTest)
            .apply(paths);
    }

    static void downloadQuarkus(Options options, LocalPaths paths)
    {
        if (LocalPaths.quarkusPomXml(paths).toFile().exists())
        {
            LOG.info("Skipping Quarkus download");
            return;
        }

        OperatingSystem.exec()
            .compose(Git.clone(options.quarkusRepo, options.quarkusBranch))
            .apply(paths.root);
    }

    static void buildQuarkus(LocalPaths paths)
    {
        if (LocalPaths.quarkusLastInstallJar(paths).toFile().exists())
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
            , LocalPaths.quarkusHome(paths)
            , Stream.of(
                LocalEnvs.graalJavaHome(paths)
            )
        );
    }

    private static OperatingSystem.Command mvnTest(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "mvn" // ?
                , "install"
                , "-Dnative"
                , "-Dno-format"
            )
            , LocalPaths.quarkusTests(paths)
            , Stream.of(
                LocalEnvs.graalJavaHome(paths)
            )
        );
    }
}

class Graal
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void buildGraal(LocalPaths paths)
    {
        if (LocalPaths.nativeImage(paths).toFile().exists())
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
                LocalPaths.mx(paths).toString()
                , "build"
            )
            , LocalPaths.svm(paths)
            , Stream.of(
                LocalEnvs.jdkJavaHome(paths)
            )
        );
    }

    static void downloadGraal(LocalPaths paths)
    {
        if (LocalPaths.svm(paths).toFile().exists())
        {
            LOG.info("Skipping Graal download");
            return;
        }

        var repo = "https://github.com/oracle/graal";
        OperatingSystem.exec()
            .compose(Git.clone(repo, "master"))
            .apply(paths.root);
    }

    static void installMx(LocalPaths paths)
    {
        if (LocalPaths.mx(paths).toFile().exists())
        {
            LOG.info("Skipping Mx install");
            return;
        }

        var repo = "https://github.com/graalvm/mx";
        OperatingSystem.exec()
            .compose(Git.clone(repo, "master"))
            .apply(paths.root);
    }

}

class Git
{
    static Function<Path, OperatingSystem.Command> clone(String repo, String branch)
    {
        return path ->
            new OperatingSystem.Command(
                Stream.of(
                    "git"
                    , "clone"
                    , repo
                    , "--depth"
                    , "1"
                    , "-b"
                    , branch
                )
                , path
                , Stream.empty()
            );
    }
}

class Java
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void installJDK(LocalPaths paths)
    {
        if (LocalPaths.java(paths).toFile().exists())
        {
            LOG.info("Skipping JDK install");
            return;
        }

        final var steps = Stream.of(
            Java.downloadJDK(paths)
            , Java.extractJDK(paths)
        );

        steps.forEach(OperatingSystem::exec);
    }

    private static OperatingSystem.Command extractJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "tar"
                , "-xzvpf"
                , "jdk.tar.gz"
                , "-C"
                , "jdk"
                , "--strip-components"
                , "1"
            )
            , paths.root
            , Stream.empty()
        );
    }

    private static OperatingSystem.Command downloadJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "curl"
                , "-L"
                , jdkURL(jdk())
                , "--output"
                , "jdk.tar.gz"
            )
            , paths.root
            , Stream.empty()
        );
    }

    private static JDK jdk()
    {
        var osName = OperatingSystem.type() == OperatingSystem.Type.MAC_OS
            ? "darwin"
            : "linux";

        return new JDK(
            "20.0-b02"
            , "11.0.6+9"
            , osName
        );
    }

    private static String jdkURL(JDK jdk)
    {
        String base = "https://github.com/graalvm/labs-openjdk-11/releases/download";
        return String.format(
            "%1$s/jvmci-%2$s/labsjdk-ce-%3$s-jvmci-%2$s-%4$s-amd64.tar.gz"
            , base
            , jdk.version
            , jdk.javaVersion
            , jdk.osName
        );
    }

    private static class JDK
    {
        final String version;
        final String javaVersion;
        final String osName;

        JDK(String version, String javaVersion, String osName)
        {
            this.version = version;
            this.javaVersion = javaVersion;
            this.osName = osName;
        }
    }
}

class LocalEnvs
{
    static OperatingSystem.EnvVar jdkJavaHome(LocalPaths paths)
    {
        return new OperatingSystem.EnvVar(
            "JAVA_HOME"
            , LocalPaths.javaHome(paths).toString()
        );
    }

    static OperatingSystem.EnvVar graalJavaHome(LocalPaths paths)
    {
        return new OperatingSystem.EnvVar(
            "JAVA_HOME"
            , LocalPaths.graalHome(paths).toString()
        );
    }
}

class LocalPaths
{
    static final Logger logger = LogManager.getLogger(LocalPaths.class);

    final Path root;
    final Path jdk;

    private LocalPaths(Path root, Path jdk)
    {
        this.root = root;
        this.jdk = jdk;
    }

    static LocalPaths newSystemPaths()
    {
        var root = OperatingSystem.mkdirs(rootPath());
        var jdk = OperatingSystem.mkdirs(root.resolve("jdk"));
        return new LocalPaths(root, jdk);
    }

    static Path javaHome(LocalPaths paths)
    {
        return OperatingSystem.type() == OperatingSystem.Type.MAC_OS
            ? paths.jdk.resolve("Contents").resolve("Home")
            : paths.jdk;
    }

    static Path java(LocalPaths paths)
    {
        final var javaPath = Path.of("bin", "java");
        return javaHome(paths).resolve(javaPath);
    }

    static Path mxHome(LocalPaths paths)
    {
        return paths.root.resolve("mx");
    }

    static Path mx(LocalPaths paths)
    {
        return mxHome(paths).resolve("mx");
    }

    static Path graalHome(LocalPaths paths)
    {
        final var graalHomePath = Path.of(
            "graal"
            , "sdk"
            , "latest_graalvm_home"
        );
        return paths.root.resolve(graalHomePath);
    }

    static Path svm(LocalPaths paths)
    {
        final var svmPath = Path.of("graal", "substratevm");
        return paths.root.resolve(svmPath);
    }

    static Path nativeImage(LocalPaths paths)
    {
        final var nativeImagePath = Path.of("bin", "native-image");
        return graalHome(paths).resolve(nativeImagePath);
    }

    static Path quarkusHome(LocalPaths paths)
    {
        return paths.root.resolve("quarkus");
    }

    static Path quarkusPomXml(LocalPaths paths)
    {
        return quarkusHome(paths).resolve("pom.xml");
    }

    static Path quarkusLastInstallJar(LocalPaths paths)
    {
        final var lastInstallJar = Path.of(
            "docs"
            , "target"
            , "quarkus-documentation-999-SNAPSHOT.jar"
        );
        return quarkusHome(paths).resolve(lastInstallJar);
    }

    static Path quarkusLastTestJar(LocalPaths paths)
    {
        final var lastTestJar = Path.of(
            "integration-tests"
            , "boostrap-config"
            , "application"
            , "target"
            , "quarkus-integration-test-bootstrap-config-application-999-SNAPSHOT.jar"
        );
        return quarkusHome(paths).resolve(lastTestJar);
    }

    static Path quarkusTests(LocalPaths paths)
    {
        return quarkusHome(paths).resolve("integration-tests");
    }

    private static Path rootPath()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        var today = date.format(formatter);
        logger.info("Today is {}", today);

        var baseDir = "target";
        logger.info("Base directory: {}", baseDir);

        final var root = Path.of(baseDir, "quarkus-with-graal", today);
        logger.info("Root path: {}", root);

        return root;
    }
}

class OperatingSystem
{
    static final Logger logger = LogManager.getLogger(OperatingSystem.class);

    public enum Type
    {
        WINDOWS, MAC_OS, LINUX, OTHER
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

    static class Command
    {
        final Stream<String> command;
        final Path directory;
        final Stream<EnvVar> envVars;

        Command(Stream<String> command, Path directory, Stream<EnvVar> envVars)
        {
            this.command = command;
            this.directory = directory;
            this.envVars = envVars;
        }
    }

    static class EnvVar
    {
        final String name;
        final String value;

        EnvVar(String name, String value)
        {
            this.name = name;
            this.value = value;
        }
    }
}

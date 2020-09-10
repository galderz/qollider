package org.mendrugo.qollider;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.INFO;

final class OperatingSystem
{
    static final System.Logger LOG = System.getLogger(Qollider.class.getName());

    final FileTree fs;

    private OperatingSystem(FileTree fs)
    {
        this.fs = fs;
    }

    static OperatingSystem of(FileTree fs)
    {
        return new OperatingSystem(fs);
    }

    void exec(Step.Exec exec)
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

    // TODO type() not unit tested, limit to getting the property
    Type type()
    {
        String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);

        if ((osName.contains("mac")) || (osName.contains("darwin")))
            return Type.MAC_OS;

        if (osName.contains("win"))
            return Type.WINDOWS;

        if (osName.contains("nux"))
            return Type.LINUX;

        return Type.UNKNOWN;
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
}

package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mendrugo.qollider.Sandbox.graal;
import static org.mendrugo.qollider.Sandbox.qollider;

public class GraalTest
{
    @Test
    void build()
    {
        Asserts.plan(
            qollider().plan(
                graal(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).build(
                    Graal.build()
                )
            )
            , Expect.gitClone("graalvm/mx", "master", 1)
            , Expect.gitClone("oracle/graal", "master", 1)
            , Expect.graalBuild()
            , Expect.graalLink()
        );
    }

    @Test
    void buildFailIfMandrel()
    {
        final var exception = assertThrows(
            IllegalArgumentException.class
            , () ->
                qollider().plan(
                    graal(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).build(
                        Graal.build("https://github.com/graalvm/mandrel/tree/mandrel/20.2")
                    )
                ).run()
        );

        assertThat(exception.getMessage(), is("Mandrel repos should be built with mandrel-build"));
    }

    @Test
    void get()
    {
        final var url = "https://doestnotexist.com/archive.tar.gz";
        Asserts.plan(
            qollider().plan(
                graal(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).get(
                    Graal.get(url)
                )
            )
            , Expect.download(url, "downloads/archive.tar.gz")
            , Expect.extract("downloads/archive.tar.gz", "graalvm")
            , Expect.link("graalvm_home", "graalvm")
        );
    }

    @Test
    void getAndDownloadNativeImage()
    {
        final var url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz";
        Asserts.plan(
            qollider().plan(
                graal(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).get(
                    Graal.get(url)
                )
            )
            , Expect.download(url, "downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz")
            , Expect.extract("downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz", "graalvm")
            , Expect.guNativeImage()
            , Expect.link("graalvm_home", "graalvm")
        );
    }
}

package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

import static org.mendrugo.qollider.Sandbox.graal;
import static org.mendrugo.qollider.Sandbox.qollider;

public class GraalTest
{
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
}

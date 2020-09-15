package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mendrugo.qollider.Sandbox.jdk;
import static org.mendrugo.qollider.Sandbox.qollider;

public class JdkTest
{
    @Test
    void get()
    {
        final var url =
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.8%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz";

        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).get(
                    Jdk.Get.of(url, "jdk")
                )
            )
            , Expect.download(url, "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz")
            , Expect.extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz", "jdk")
            , Expect.link("java_home", "jdk/Contents/Home")
        );
    }

    @Test
    void getBootJdkMacOs()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).getBoot(
                    new Jdk.Build(Repository.of(
                        "https://github.com/openjdk/jdk11u-dev/tree/master"
                    ))
                )
            )
            , Expect.jdk11DownloadMacOs()
            , Expect.bootJdk11ExtractMacOs()
            , Expect.link("bootjdk_home", "boot-jdk-11/Contents/Home")
        );
    }
}

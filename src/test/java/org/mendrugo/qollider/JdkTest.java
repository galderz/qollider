package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

public class JdkTest
{
    @Test
    void get()
    {
        final var url =
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.8%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz";

        Asserts.plan(
            Sandbox.qollider().plan(
                Sandbox.jdk().get(
                    Jdk.Get.of(url, "jdk")
                )
            )
            , Expect.download(url, "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz")
            , Expect.extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz", "jdk")
            , Expect.link("java_home", "jdk/Contents/Home")
        );
    }
}

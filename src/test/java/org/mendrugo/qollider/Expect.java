package org.mendrugo.qollider;

import java.nio.file.Path;

record Expect(Step step, boolean touched)
{
    static Expect bootJdk11ExtractLinux()
    {
        return extract("downloads/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz", "boot-jdk-11");
    }

    static Expect bootJdk11ExtractMacOs()
    {
        return extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz", "boot-jdk-11");
    }

    static Expect bootJdk14Extract()
    {
        return extract("downloads/OpenJDK14U-jdk_aarch64_linux_hotspot_14.0.2_12.tar.gz", "boot-jdk-14");
    }

    static Expect bootJdk11LinkLinux()
    {
        return Expect.of(new Step.Linking(
            Path.of("bootjdk_home")
            , Path.of("boot-jdk-11")
        ));
    }

    static Expect extract(String tar, String path)
    {
        return Expect.of(Step.Exec.of(
            Path.of("")
            , "tar"
            , "-xzpf"
            , tar
            , "-C"
            , path
            , "--strip-components"
            , "1"
        ));
    }

    static Expect download(String url, String path)
    {
        return Expect.of(new Step.Download(
            URLs.of(url)
            , Path.of(path)
        ));
    }

    static Expect gitClone(String repo, String branch)
    {
        return Expect.of(Step.Exec.of(
            "git"
            , "clone"
            , "-b"
            , branch
            , "--depth"
            , "1"
            , String.format("https://github.com/%s", repo)
        ));
    }

    static Expect gitCloneFull(String repo, String branch)
    {
        return Expect.of(Step.Exec.of(
            "git"
            , "clone"
            , "-b"
            , branch
            , String.format("https://github.com/%s", repo)
        ));
    }

    static Expect jdk11DownloadLinux()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz"
            , "downloads/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz"
        );
    }

    static Expect jdk11DownloadMacOs()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
            , "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
        );
    }

    static Expect jdk14DownloadLinux()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.2%2B12/OpenJDK14U-jdk_aarch64_linux_hotspot_14.0.2_12.tar.gz"
            , "downloads/OpenJDK14U-jdk_aarch64_linux_hotspot_14.0.2_12.tar.gz"
        );
    }

    static Expect link(String link, String target)
    {
        return Expect.of(new Step.Linking(
            Path.of(link)
            , Path.of(target)
        ));
    }

    static Expect of(Step step)
    {
        return new Expect(step, true);
    }
}

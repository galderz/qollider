package org.mendrugo.qollider;

public final class Repositories
{
    public static final Repository INFINISPAN = github("infinispan", "infinispan");
    public static final Repository INFINISPAN_QUARKUS = github("infinispan", "infinispan-quarkus");

    public static final Repository JDK_JDK = github("openjdk", "jdk");
    public static final Repository JDK_11_DEV = github("openjdk", "jdk11u-dev");

    // 20.1 branch includes workarounds for building with XCode 12 on macOS:
    // https://bugs.openjdk.java.net/browse/JDK-8253375
    // https://bugs.openjdk.java.net/browse/JDK-8253791
    public static final Repository LABS_JDK_20_1 = github("galderz", "labs-openjdk-11", "jvmci-20.1-b04_8253375");
    public static final Repository LABS_JDK_20_3 = github("graalvm", "labs-openjdk-11", "jvmci-20.3-b06");
    public static final Repository LABS_JDK = LABS_JDK_20_3;
    public static final Repository GRAAL = github("oracle", "graal");

    public static final Repository MANDREL_20_1 = github("graalvm", "mandrel", "mandrel/20.1");
    public static final Repository MANDREL_20_2 = github("graalvm", "mandrel", "mandrel/20.2");
    public static final Repository MANDREL_20_3 = github("graalvm", "mandrel", "mandrel/20.3");
    public static final Repository MANDREL = MANDREL_20_3;
    public static final Repository MANDREL_PACKAGING = github("graalvm", "mandrel-packaging");

    public static final Repository MX = github("graalvm", "mx");

    public static final Repository QUARKUS = github("quarkusio", "quarkus");

    public static Repository github(String org, String name)
    {
        return github(org, name, "master");
    }

    public static Repository github(String org, String name, String branch)
    {
        return Repository.of(
            String.format(
                "https://github.com/%s/%s/tree/%s"
                , org
                , name
                , branch
            )
        );
    }

    private Repositories() {}
}

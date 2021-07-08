package org.mendrugo.qollider;

public final class Repositories
{
    public static final Repository INFINISPAN = github("infinispan", "infinispan");
    public static final Repository INFINISPAN_QUARKUS = github("infinispan", "infinispan-quarkus");

    public static final Repository JDK_JDK = github("openjdk", "jdk");
    public static final Repository JDK_11_DEV = github("openjdk", "jdk11u-dev");

    public static final Repository LABS_JDK_20_3 = github("graalvm", "labs-openjdk-11", "jvmci-20.3-b18");
    public static final Repository LABS_JDK_21_2 = github("graalvm", "labs-openjdk-11", "jvmci-21.2-b05");
    public static final Repository LABS_JDK = LABS_JDK_21_2;
    public static final Repository GRAAL_20_3 = github("oracle", "graal", "release/graal-vm%2F20.3");
    public static final Repository GRAAL_21_2 = github("oracle", "graal", "release/graal-vm%2F21.2");
    public static final Repository GRAAL = github("oracle", "graal");

    public static final Repository MANDREL_20_1 = github("graalvm", "mandrel", "mandrel/20.1");
    public static final Repository MANDREL_20_2 = github("graalvm", "mandrel", "mandrel/20.2");
    public static final Repository MANDREL_20_3 = github("graalvm", "mandrel", "mandrel/20.3");
    public static final Repository MANDREL = MANDREL_20_3;
    public static final Repository MANDREL_PACKAGING = github("graalvm", "mandrel-packaging");

    public static final Repository MX = github("graalvm", "mx");

    public static final Repository QUARKUS = github("quarkusio", "quarkus", "main");

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

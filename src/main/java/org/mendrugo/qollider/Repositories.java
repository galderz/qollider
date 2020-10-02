package org.mendrugo.qollider;

public final class Repositories
{
    public static final Repository JDK_JDK = Repository.of("https://github.com/openjdk/jdk/tree/master");
    public static final Repository JDK_11_DEV = Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master");

    public static final Repository GRAAL = Repository.of("https://github.com/oracle/graal/tree/master");
    public static final Repository MX = Repository.of("https://github.com/graalvm/mx/tree/master");

    public static final Repository MANDREL_20_2 = Repository.of("https://github.com/graalvm/mandrel/tree/mandrel/20.2");
    public static final Repository MANDREL = MANDREL_20_2;
}

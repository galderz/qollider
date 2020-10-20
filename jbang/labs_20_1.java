//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.1.0}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repository;

/**
 * Script to build labs JDK 20.1.
 * Builds branch that includes workarounds for building with XCode 12 on macOS:
 * https://bugs.openjdk.java.net/browse/JDK-8253375
 * https://bugs.openjdk.java.net/browse/JDK-8253791
 */
public class labs_20_1
{
    public static void main(String... args)
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(
                        Repository.of("https://github.com/galderz/labs-openjdk-11/tree/jvmci-20.1-b04_8253375")
                    )
                )
            )
            .run();
    }
}

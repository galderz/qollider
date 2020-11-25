//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.1.0}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;

/**
 * Script to build labs JDK 20.3.
 */
public class labs_20_3
{
    public static void main(String... args)
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(Repositories.LABS_JDK_20_3))
                )
            )
            .run();
    }
}

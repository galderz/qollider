//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.1.0}

import org.mendrugo.qollider.Qollider;

/**
 * Script to get git version
 */
public class git_version
{
    public static void main(String... args)
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.git().version()
            )
            .run();
    }
}

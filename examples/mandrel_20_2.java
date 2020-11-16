//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.1.0}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Mandrel;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;

/**
 * Script to build mandrel 20.2 branch.
 */
public class mandrel_20_2
{
    public static void main(String... args)
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(Repositories.JDK_11_DEV)
                )
                , qollider.mandrel().build(
                    new Mandrel.Build(
                        Repositories.MANDREL_20_2
                        , Repositories.MX
                        , Repositories.MANDREL_PACKAGING
                    )
                )
            )
            .run();
    }
}

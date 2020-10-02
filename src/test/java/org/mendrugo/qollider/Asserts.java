package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Link;
import org.mendrugo.qollider.Qollider.Output;
import org.mendrugo.qollider.Qollider.Plan;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public final class Asserts
{
    public static void plan(Plan plan, Expect... expects)
    {
        final var result = plan.run();
        assertThat(result.items().toString(), result.items().size(), is(expects.length));
        for (int i = 0; i < expects.length; i++)
        {
            final Output output = result.items().get(i);
            if (output instanceof Marker marker)
            {
                step(marker, expects[i]);
            }
            else if (output instanceof Link link)
            {
                step(link, expects[i]);
            }
//            else if (output instanceof Step.Exec.Eager eager)
//            {
//                step(eager, expects[i]);
//            }
            else
            {
                throw new IllegalStateException(String.format(
                    "Unknown output implementation for: %s"
                    , output
                ));
            }
        }
    }

    private static void step(Marker actual, Expect expected)
    {
        assertThat(actual.cause(), is(expected.step().toString()));
        assertThat(actual.exists(), is(true));
        assertThat(actual.touched(), is(expected.touched()));
        assertThat(
            actual.path().toString()
            , is(format("%s.marker", Hashing.sha1(expected.step().toString())))
        );
    }

    static void step(Link actual, Expect expected)
    {
        // TODO avoid cast? the only step that can produce a Link is Linking...
        final var linking = (Step.Linking) expected.step();
        assertThat(actual.link(), is(linking.link()));
        assertThat(actual.target(), is(linking.target()));
    }

//    static void step(Steps.Exec.Eager actual, Expect expected)
//    {
//        assertThat(actual.exec(), is(expected.step));
//    }
}

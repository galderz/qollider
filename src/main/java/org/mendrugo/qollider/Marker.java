package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Output;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;

// TODO make path latest rather than day (makes the build transferable across days)
// Boundary value
record Marker(boolean exists, boolean touched, Path path, String cause) implements Output
{
    Marker query(Predicate<Path> existsFn)
    {
        final var exists = existsFn.test(this.path);
        if (exists)
        {
            return new Marker(true, touched, path, cause);
        }

        return new Marker(false, touched, path, cause);
    }

    Marker touch(Function<Marker, Boolean> touchFn)
    {
        if (exists)
        {
            return this;
        }

        final var touched = touchFn.apply(this);
        if (touched)
        {
            return new Marker(true, true, path, cause);
        }

        return new Marker(false, false, path, cause);
    }

    // TODO don't take all the info for the step
    // for git -> git clone + repo name (ignore branch, depth...etc)
    // for maven build -> ignore additional params
    // for maven test -> ignore additional params
    static Marker of(Step step)
    {
        final var producer = step.toString();
        final var hash = Hashing.sha1(producer);
        final var path = Path.of(format("%s.marker", hash));
        return new Marker(false, false, path, producer);
    }
}

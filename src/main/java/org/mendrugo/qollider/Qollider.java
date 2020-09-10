package org.mendrugo.qollider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Qollider
{
    Plan plan(Action<?, ?>... actions)
    {
        return () ->
        {
            final var result = new ArrayList<Output>(actions.length);
            for (Action action : actions)
            {
                final var output =
                    (Output) action.action().apply(action.step, action.effects);
                result.add(output);
            }
            return new Result(result);
        };
    }

    record Action<S, E>(
        S step
        , E effects
        , BiFunction<S, E, ? extends Output> action
    ) {}

    interface Output {}

    record Result(List<? extends Output> items) {}

    interface Plan
    {
        Result run();
    }

    record Link(Path link, Path target) implements Output {}
}

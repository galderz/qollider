package org.mendrugo.qollider;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Qollider
{
    Plan plan(Action... actions)
    {
        return () ->
        {
            final List<Output> result = Arrays.stream(actions)
                .flatMap(action -> action.items().stream())
                .map(Supplier::get)
                .collect(Collectors.toList());
            return new Result(result);
        };
    }

    record Action(List<Supplier<Output>> items) {}

    interface Output {}

    record Result(List<Output> items) {}

    interface Plan
    {
        Result run();
    }

    record Link(Path link, Path target) implements Output {}
}

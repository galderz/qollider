package org.mendrugo.qollider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class Qollider
{
    Plan plan(Action<? extends Step, ? extends Effect> action)
    {
        return () ->
        {
            final Output result = action.action().apply((Step) action.step, (Effect) action.effects);
            return new Result(List.of(result));
        };
    }

//    Plan plan(Action<Step, Effect>... actions)
//    {
//        return () ->
//        {
//            final var result = new ArrayList<Output>(actions.length);
//            for (Action<Step, Effect> action : actions)
//            {
//                final var output =
//                    (Output) action.action().apply(action.step, action.effects);
//                result.add(output);
//            }
//            return new Result(result);
//        };
//    }
//
    record Action<S extends Step, E extends Effect>(
        S step
        , E effects
        , BiFunction<? super S, ? super E, ? extends Output> action
    ) {}

    interface Output {}

    record Result(List<? extends Output> items) {}

    interface Plan
    {
        Result run();
    }

    record Link(Path link, Path target) implements Output {}
}

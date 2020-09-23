package org.mendrugo.qollider;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Qollider
{
    private final Effects effects;

    Qollider(Effects effects)
    {
        this.effects = effects;
    }

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

    Jdk jdk()
    {
        return new Jdk(effects.lazy(), effects.install(), effects.linking(), effects.roots());
    }

    Graal graal()
    {
        return new Graal(effects.lazy(), effects.install(), effects.linking(), effects.roots());
    }

    record Action(List<Supplier<Output>> items)
    {
        static Action of(Action... actions)
        {
            return Action.of(List.of(actions));
        }

        static Action of(List<Action> actions)
        {
            return new Action(
                actions.stream()
                    .flatMap(action -> action.items().stream())
                    .collect(Collectors.toList())
            );
        }

        static Action of(Supplier<Output> item)
        {
            return new Action(List.of(item));
        }

        static Action of(Supplier<Output> item0, Supplier<Output> item1)
        {
            return new Action(List.of(item0, item1));
        }
    }

    interface Output {}

    record Result(List<Output> items) {}

    interface Plan
    {
        Result run();
    }

    record Link(Path link, Path target) implements Output {}

    // TODO refactor and move to Effect
    record Roots(Function<Path, Path> home, Function<Path, Path> today) {}

    record Get(URL url, Path path)
    {
        // TODO makes sense with static methods in Jdk...?
        static Get of(String url, String path)
        {
            return new Get(URLs.of(url), Path.of(path));
        }
    }
}

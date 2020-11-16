package org.mendrugo.qollider;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.INFO;

public final class Qollider
{
    static final System.Logger LOG = System.getLogger(Qollider.class.getName());

    private final Effects effects;
    private final Path home;
    private final Path today;

    public Qollider(Effects effects, Path home, Path today) {
        this.effects = effects;
        this.home = home;
        this.today = today;
    }

    public static Qollider of()
    {
        final var home = FileTree.home();
        final var today = FileTree.today(home);
        return new Qollider(Effects.of(), home, today);
    }

    public Plan plan(Action... actions)
    {
        return () ->
        {
            final List<Output> result = Arrays.stream(actions)
                .flatMap(action -> action.items().stream())
                .map(Supplier::get)
                .collect(Collectors.toList());

            LOG.log(INFO, "Execution summary:");

//            TODO Track inputs
//            log.log(INFO, "Inputs:{0}{1}"
//                , System.lineSeparator()
//                , List.of(args).stream()
//                    .map(Object::toString)
//                    .collect(Collectors.joining(System.lineSeparator()))
//            );

            LOG.log(INFO, "Outputs:{0}{1}"
                , System.lineSeparator()
                , result.stream()
                    .map(Qollider::showOutput)
                    .collect(Collectors.joining(System.lineSeparator()))
            );

            return new Result(result);
        };
    }

    private static String showOutput(Output output)
    {
        if (output instanceof Marker m)
        {
            return String.format("%s%s", m.cause(), m.touched() ? "" : " (skipped)");
        }
        else
        {
            return output.toString();
        }
    }

    public Git git()
    {
        return new Git(effects, today);
    }

    public Jdk jdk()
    {
        return new Jdk(effects, home, today);
    }

    public Graal graal()
    {
        return new Graal(effects, today);
    }

    public Mandrel mandrel()
    {
        return new Mandrel(effects, today);
    }

    public record Action(List<Supplier<Output>> items)
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

    public interface Plan
    {
        Result run();
    }

    // TODO make private
    record Link(Path link, Path target) implements Output
    {
        @Override
        public String toString()
        {
            return String.format(
                "\uF07C %s%n$ ln -s %s %s%n"
                , link.getParent()
                , target
                , link.getFileName()
            );
        }
    }

    record Get(URL url, Path path)
    {
        // TODO makes sense with static methods in Jdk...?
        static Get of(String url, String path)
        {
            return new Get(URLs.of(url), Path.of(path));
        }
    }
}

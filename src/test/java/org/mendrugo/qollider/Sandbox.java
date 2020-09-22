package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Roots;

import java.nio.file.Path;

final class Sandbox
{
    Effect.Exec.Lazy lazy()
    {
        return new Effect.Exec.Lazy(this::exists, e -> {}, this::touch);
    }

    boolean exists(Path path)
    {
        // TODO check for existence
        return false;
    }

    Effect.Install install(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        return new Effect.Install(
            new Effect.Download(this::exists, this::touch, d -> {}, () -> osType, () -> arch)
            , new Effect.Extract(lazy(), p -> {})
        );
    }

    Effect.Linking linking()
    {
        return new Effect.Linking(Qollider.Link::new);
    }

    boolean touch(Marker marker)
    {
        return true;
    }

    private static Sandbox empty()
    {
        return new Sandbox();
    }

    static Git git()
    {
        return new Git(Sandbox.empty().lazy());
    }

    static Jdk jdk(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var sandbox = Sandbox.empty();
        return new Jdk(
            sandbox.lazy()
            , sandbox.install(osType, arch)
            , sandbox.linking()
            , roots()
        );
    }

    static Graal graal(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var sandbox = Sandbox.empty();
        return new Graal(
            sandbox.lazy()
            , sandbox.install(osType, arch)
            , sandbox.linking()
            , roots()
        );
    }

    static Qollider qollider()
    {
        return new Qollider();
    }

    private static Roots roots()
    {
        return new Roots(
            p -> Path.of("/", "home").resolve(p)
            , p -> Path.of("/", "today").resolve(p)
        );
    }
}

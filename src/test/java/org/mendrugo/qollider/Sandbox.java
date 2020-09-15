package org.mendrugo.qollider;

import java.nio.file.Path;

final class Sandbox
{
    Effect.Exec.Lazy execLazy()
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
            , new Effect.Extract(execLazy(), p -> {})
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
        return new Git(Sandbox.empty().execLazy());
    }

    static Jdk jdk(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var sandbox = Sandbox.empty();
        return new Jdk(
            sandbox.install(osType, arch)
            , sandbox.linking()
        );
    }

    static Qollider qollider()
    {
        return new Qollider();
    }
}

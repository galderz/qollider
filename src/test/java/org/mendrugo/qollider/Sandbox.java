package org.mendrugo.qollider;

import java.nio.file.Path;

public final class Sandbox
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

    public static Git git()
    {
        return new Git(Sandbox.empty().lazy());
    }

    public static Qollider qolliderMacOs()
    {
        return qollider(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64);
    }

    public static Qollider qolliderLinux()
    {
        return qollider(OperatingSystem.Type.LINUX, Hardware.Arch.AARCH64);
    }

    public static Qollider qolliderUnknown()
    {
        return qollider(OperatingSystem.Type.UNKNOWN, Hardware.Arch.UNKNOWN);
    }

    private static Qollider qollider(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var home = Path.of("/", "home");
        final var today = Path.of("/", "today");
        return new Qollider(effects(home, osType, arch), effects(today, osType, arch));
    }

    private static Effects effects(Path root, OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var sandbox = new Sandbox();
        return new Effects(
            sandbox.lazy()
            , sandbox.install(osType, arch)
            , sandbox.linking()
            , root
        );
    }
}

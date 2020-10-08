package org.mendrugo.qollider;

import java.nio.file.Path;

public final class Sandbox
{
    public static Git git()
    {
        return new Git(Sandbox.empty().lazy(), today());
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

    public static Path home()
    {
        return Path.of("/", "home");
    }

    public static Path today()
    {
        return Path.of("/", "today");
    }

    private static Qollider qollider(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        return new Qollider(effects(osType, arch), home(), today());
    }

    private static Effects effects(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        final var sandbox = new Sandbox();
        return new Effects(
            sandbox.lazy()
            , sandbox.install(osType, arch)
            , sandbox.linking()
        );
    }

    private Effect.Exec.Lazy lazy()
    {
        return new Effect.Exec.Lazy(this::exists, e -> {}, this::touch);
    }

    private boolean exists(Path path)
    {
        // TODO check for existence
        return false;
    }

    private Effect.Install install(OperatingSystem.Type osType, Hardware.Arch arch)
    {
        return new Effect.Install(
            new Effect.Download(this::exists, this::touch, d -> {}, () -> osType, () -> arch)
            , new Effect.Extract(lazy(), p -> {})
        );
    }

    private Effect.Linking linking()
    {
        return new Effect.Linking(Qollider.Link::new);
    }

    private boolean touch(Marker marker)
    {
        return true;
    }

    private static Sandbox empty()
    {
        return new Sandbox();
    }
}

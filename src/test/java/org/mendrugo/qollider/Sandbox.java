package org.mendrugo.qollider;

import java.nio.file.Path;

import static org.mendrugo.qollider.Step.*;

final class Sandbox
{
    Exec.Lazy.Effects lazyExec()
    {
        return new Exec.Lazy.Effects(this::exists, e -> {}, this::touch);
    }

    boolean exists(Path path)
    {
        return false;
    }

    boolean touch(Marker marker)
    {
        return true;
    }

    static Sandbox empty()
    {
        return new Sandbox();
    }

    static Git git()
    {
        return new Git(Sandbox.empty().lazyExec());
    }

    static Qollider qollider()
    {
        return new Qollider();
    }
}

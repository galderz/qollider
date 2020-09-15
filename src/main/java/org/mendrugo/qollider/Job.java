package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Output;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public interface Job
{
    // Download + Extract
    record Install(URL url, Path path) implements Job
    {
        static List<Supplier<Output>> install(Job.Install install, Effect.Install effects)
        {
            final var url = install.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var tarPath = directory.resolve(fileName);

            final var downloadMarker = Step.Download.action(
                new Step.Download(url, tarPath)
                , effects.download()
            );

            final var extractMarker = Step.Extract.action(
                new Step.Extract(tarPath, install.path)
                , effects.extract()
            );

            return List.of(downloadMarker, extractMarker);
        }
    }
}

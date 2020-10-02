package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.net.URL;
import java.nio.file.Path;

interface Job
{
    // Download + Extract
    record Install(URL url, Path path) implements Job
    {
        static Action install(Job.Install install, Effect.Install effects)
        {
            final var url = install.url;
            final var fileName = Path.of(url.getFile()).getFileName();
            final var directory = Path.of("downloads");
            final var tarPath = directory.resolve(fileName);

            final var downloadAction = Step.Download.action(
                new Step.Download(url, tarPath)
                , effects.download()
            );

            final var extractAction = Step.Extract.action(
                new Step.Extract(tarPath, install.path)
                , effects.extract()
            );

            return Action.of(downloadAction, extractAction);
        }
    }
}

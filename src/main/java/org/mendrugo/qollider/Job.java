package org.mendrugo.qollider;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

public interface Job
{
//    // Download + Extract
//    record Install(URL url, Path path)
//    {
//        static List<Marker> install(Install install, Install.Effects effects)
//        {
//            final var url = install.url;
//            final var fileName = Path.of(url.getFile()).getFileName();
//            final var directory = Path.of("downloads");
//            final var tarPath = directory.resolve(fileName);
//
//            final var downloadMarker = Step.Download.lazy(
//                new Step.Download(url, tarPath)
//                , effects.download
//            );
//
//            final var extractMarker = Step.Extract.extract(
//                new Step.Extract(tarPath, install.path)
//                , effects.extract
//            );
//
//            return List.of(downloadMarker, extractMarker);
//        }
//
//        record Effects(Step.Download.Effects download, Step.Extract.Effects extract)
//        {
//            static Effects of(Web web, OperatingSystem os)
//            {
//                final var hw = new Hardware();
//                final var download = Step.Download.Effects.of(web, os, hw);
//                final var extract = Step.Extract.Effects.of(os);
//                return new Effects(download, extract);
//            }
//        }
//    }
}

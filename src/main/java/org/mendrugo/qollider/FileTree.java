package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;

final class FileTree
{
    static final System.Logger LOG = System.getLogger(Qollider.class.getName());

    static Path today(Path home)
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("ddMM");
        var today = date.format(formatter);
        var baseDir = home.resolve("cache");

        final var baseToday = baseDir.resolve(today);
        // Check whether it's a new day before creating directories
        final var isNewDay = !baseToday.toFile().exists();
        FileTree.idempotentMkDirs(baseToday);

        final var latest = baseDir.resolve(Path.of("latest"));
        if (isNewDay)
        {
            FileTree.symlink(latest, Path.of(today));
        }
        return latest;
    }

    static Path home()
    {
        var home = Path.of(System.getProperty("user.home"), ".qollider");
        FileTree.idempotentMkDirs(home);
        return home;
    }

    private static Path idempotentMkDirs(Path directory)
    {
        final var directoryFile = directory.toFile();
        if (!directoryFile.exists() && !directoryFile.mkdirs())
        {
            throw new RuntimeException(format(
                "Unable to create directory: %s"
                , directory)
            );
        }

        return directory;
    }

    static void mkdirs(Path directory)
    {
        FileTree.idempotentMkDirs(directory);
    }

    static boolean exists(Path path)
    {
        return path.toFile().exists();
    }

    static boolean touch(Marker marker)
    {
        final var path = marker.path();
        try
        {
            Files.writeString(path, marker.cause());
            return true;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    static Link symlink(Path link, Path relativeTarget)
    {
        try
        {
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS))
            {
                Files.delete(link);
            }

            final var symbolicLink = Files.createSymbolicLink(link, relativeTarget);
            return new Link(symbolicLink, relativeTarget);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

//    Path resolve(Path other)
//    {
//        return root.resolve(other);
//    }
//
//    // TODO use again when reimplementing clean
//    void deleteRecursive(Path relative)
//    {
//        try
//        {
//            final var path = root.resolve(relative);
//
//            final var notDeleted =
//                Files.walk(path)
//                    .sorted(Comparator.reverseOrder())
//                    .map(Path::toFile)
//                    .filter(Predicate.not(File::delete))
//                    .collect(Collectors.toList());
//
//            if (!notDeleted.isEmpty())
//            {
//                throw new RuntimeException(format(
//                    "Unable to delete %s files"
//                    , notDeleted
//                ));
//            }
//        }
//        catch (IOException e)
//        {
//            throw new RuntimeException(e);
//        }
//    }
}

package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Link;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;

final class FileTree
{
    static final System.Logger LOG = System.getLogger(Qollider.class.getName());

    final Path root;

    static FileTree ofToday(FileTree home)
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("ddMM");
        var today = date.format(formatter);
        var baseDir = home.root.resolve("cache");
        final var baseToday = baseDir.resolve(today);
        final var isNewDay = !baseToday.toFile().exists();
        idempotentMkDirs(baseToday);
        if (isNewDay)
        {
            home.symlink(Path.of("cache", "latest"), Path.of(today));
        }
        return new FileTree(baseDir.resolve("latest"));
    }

    static FileTree ofHome()
    {
        var home = Path.of(System.getProperty("user.home"), ".qollider");
        return new FileTree(idempotentMkDirs(home));
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

    void mkdirs(Path directory)
    {
        FileTree.idempotentMkDirs(root.resolve(directory));
    }

    boolean exists(Path path)
    {
        return root.resolve(path).toFile().exists();
    }

    boolean touch(Marker marker)
    {
        final var path = root.resolve(marker.path());
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

    Link symlink(Path relativeLink, Path relativeTarget)
    {
        final var link = root.resolve(relativeLink);
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

    private FileTree(Path root)
    {
        this.root = root;
    }
}

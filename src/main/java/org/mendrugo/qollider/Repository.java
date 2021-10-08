package org.mendrugo.qollider;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public interface Repository
{
    String host();
    String organization();
    String name();
    Repository.Type type();

    default String cloneUri()
    {
        return String.format("https://%s/%s/%s", host(), organization(), name());
    }

    static Repository of(String uri)
    {
        return Repository.of(URI.create(uri));
    }

    static Repository of(URI uri)
    {
        final var host = uri.getHost();
        final var path = Path.of(uri.getPath());
        if (host.equals("github.com"))
        {
            final var organization = path.getName(0).toString();
            final var name = path.getName(1).toString();
            switch (path.getName(2).toString())
            {
                case "tree" ->
                {
                    final var branch = extractBranch(path).toString();
                    final var depth = extractDepth(uri, name);
                    return new Branch(host, organization, name, Type.GIT, branch, depth);
                }
                case "commit" ->
                {
                    final var commitId = path.getName(3).toString();
                    return new Commit(host, organization, name, Type.GIT, commitId);
                }
                default ->
                    throw new IllegalArgumentException(String.format("Unknown Github repo URL: %s", uri));
            }
        }
        else if (host.equals("hg.openjdk.java.net"))
        {
            final var organization = path.getName(0).toString();
            final var name = path.getFileName().toString();
            return new Commit(host, organization, name, Type.MERCURIAL, null);
        }
        throw Illegal.value(host);
    }

    record Branch(
        String host
        , String organization
        , String name
        , Repository.Type type
        , String branch
        , int depth
    ) implements Repository {}

    record Commit(
        String host
        , String organization
        , String name
        , Repository.Type type
        , String commitId
    ) implements Repository {}

    enum Type
    {
        GIT
        , MERCURIAL
    }

    private static Path extractBranch(Path path)
    {
        final int base = 3;
        if (path.getNameCount() == (base + 1))
            return path.getFileName();

        final var numPathElements = path.getNameCount() - base;
        final var pathElements = new String[numPathElements];
        int index = 0;
        while (index < numPathElements)
        {
            pathElements[index] = path.getName(base + index).toString();
            index++;
        }

        final var first = pathElements[0];
        final var numMoreElements = numPathElements - 1;
        final var more = new String[numMoreElements];
        System.arraycopy(pathElements, 1, more, 0, numMoreElements);
        return Path.of(first, more);
    }

    private static int extractDepth(URI uri, String repoName)
    {
        final var params = URIs.splitQuery(uri);
        final var value = params.get("depth");
        if (Objects.nonNull(value))
        {
            return Integer.parseInt(value);
        }

        return repoName.equals("labs-openjdk-11")
            ? 40
            : 1;
    }
}

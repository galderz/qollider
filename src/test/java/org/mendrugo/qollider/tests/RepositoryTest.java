package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Repository;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RepositoryTest
{
    @Test
    void githubFactory()
    {
        final var repo = Repository.github("openjdk", "jdk", "master");
        assertThat(repo.organization(), is("openjdk"));
        assertThat(repo.name(), is("jdk"));
        assertThat(repo.branch(), is("master"));
        assertThat(repo.depth(), is(1));
        assertThat(repo.cloneUri(), is(URI.create("https://github.com/openjdk/jdk")));
    }
}

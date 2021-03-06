/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tomitribe.lieutenant.git;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

//WARNING: To run this test you need to have git CLI on path

public class GitTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File gitRepo;

    @Before
    public void createGitRepo() throws IOException, TimeoutException, InterruptedException {
        this.gitRepo = temporaryFolder.newFolder();
        final int exit = new ProcessExecutor().command("git", "init", this.gitRepo.getAbsolutePath())
                .execute().getExitValue();

        if (exit != 0) {
            throw new RuntimeException();
        }
    }

    @Test
    public void shouldCreateGitRepoModelIfPointsToGitRepo() {
        final Git git = new Git(this.gitRepo);
        assertThat(git, notNullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfCurrentDirectoryIsNotAGitRepo() throws IOException {
        final File noneGitFolder = temporaryFolder.newFolder();
        final Git git = new Git(noneGitFolder);
    }

    @Test
    public void shouldReadCurrentBranch() {
        final Git git = new Git(this.gitRepo);
        final String currentBranch = git.currentBranch();
        assertThat(currentBranch, is("master"));
    }

    @Test
    public void shouldListBranches() throws InterruptedException, TimeoutException, IOException {
        commitFile();

        final int exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "checkout", "-b", "test").execute().getExitValue();

        if (exitValue != 0) {
            throw new RuntimeException();
        }

        final Git git = new Git(this.gitRepo);
        final Set<String> branches = git.branchList();

        assertThat(branches, hasItems("master", "test"));

    }

    @Test
    public void shouldReturnCurrentCommitIdOfGivenBranch() throws IOException, TimeoutException, InterruptedException {
        final Git git = new Git(this.gitRepo);

        commitFile();
        final String commit = new ProcessExecutor().directory(this.gitRepo).command("git", "rev-parse", "--short", "HEAD")
                .readOutput(true).execute()
                .outputUTF8().trim();

        assertThat(git.trunkedLatestCommit(git.currentBranch()), is(commit));
    }

    private void commitFile() throws IOException, InterruptedException, TimeoutException {
        Files.write(Paths.get(this.gitRepo.getAbsolutePath(), "hello.txt"), "Hello".getBytes());
        int exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "add", ".")
                .execute().getExitValue();

        if (exitValue != 0) {
            throw new RuntimeException();
        }

        exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "commit", "-m", "\"Initial Commit\"")
                .execute().getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException();
        }
    }

    @Test
    public void shouldReturnCleanIfDirectoryIsNotDirty() throws InterruptedException, TimeoutException, IOException {
        final Git git = new Git(this.gitRepo);

        commitFile();

        assertThat(git.isDirty(), is(false));
    }

    @Test
    public void shouldReturnDiryIfDirectoryContainsAddedFiles() throws InterruptedException, TimeoutException, IOException {
        final Git git = new Git(this.gitRepo);

        Files.write(Paths.get(this.gitRepo.getAbsolutePath(), "hello.txt"), "Hello".getBytes());
        int exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "add", ".")
                .execute().getExitValue();

        if (exitValue != 0) {
            throw new RuntimeException();
        }

        assertThat(git.isDirty(), is(true));
    }

    @Test
    public void shouldReturnDirtyIfDirectoryContainsUntrackedFiles() throws IOException {
        final Git git = new Git(this.gitRepo);

        Files.write(Paths.get(this.gitRepo.getAbsolutePath(), "hello.txt"), "Hello".getBytes());

        assertThat(git.isDirty(), is(true));
    }


    @Test
    public void shouldReturnTagsOfGivenCommit() throws IOException, TimeoutException, InterruptedException {

        final Git git = new Git(this.gitRepo);

        commitFile();

        int exitValue = new ProcessExecutor().directory(this.gitRepo)
                .command("git", "tag", "-a", "v1.4", "-m", "\"my version 1.4\"")
                .execute().getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException();
        }

        String trunkedCommit = git.trunkedLatestCommit(git.currentBranch());
        assertThat(git.tagList(trunkedCommit), CoreMatchers.hasItem("v1.4"));
    }

    @Test
    public void shouldReturnNoTagsIfGivenCommitHasNoTag() throws IOException, TimeoutException, InterruptedException {

        final Git git = new Git(this.gitRepo);

        commitFile();

        int exitValue = new ProcessExecutor().directory(this.gitRepo)
                .command("git", "tag", "-a", "v1.4", "-m", "\"my version 1.4\"")
                .execute().getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException();
        }

        Files.write(Paths.get(this.gitRepo.getAbsolutePath(), "hello2.txt"), "Hello".getBytes());
        exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "add", ".")
                .execute().getExitValue();

        if (exitValue != 0) {
            throw new RuntimeException();
        }

        exitValue = new ProcessExecutor().directory(this.gitRepo).command("git", "commit", "-m", "\"Second Commit\"")
                .execute().getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException();
        }

        String trunkedCommit = git.trunkedLatestCommit(git.currentBranch());
        assertThat(git.tagList(trunkedCommit).size(), CoreMatchers.is(0));
    }

}

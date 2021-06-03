package io.jenkins.plugins.adobe.cloudmanager.builder;

/*-
 * #%L
 * Adobe Cloud Manager Plugin
 * %%
 * Copyright (C) 2020 - 2021 Adobe Inc.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.tasks.Builder;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitSampleRepoRule;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

public class RepositorySyncBuilderTest {

  private static final String defaultBranch = "customDefaultBranch";

  @ClassRule
  public static BuildWatcher watcher = new BuildWatcher();

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Rule
  public GitSampleRepoRule srcRepo = new GitSampleRepoRule();

  @Rule
  public GitSampleRepoRule bareDestRepo = new GitSampleRepoRule();

  @Rule
  public GitSampleRepoRule destRepo = new GitSampleRepoRule();

  @Rule
  public GitSampleRepoRule updatedDestRepo = new GitSampleRepoRule();

  @BeforeClass
  public static void beforeClass() throws Exception {
    CliGitCommand cmd = new CliGitCommand(null);
    cmd.setDefaults();
  }

  @Before
  public void before() throws Exception {
    srcRepo.init();
    srcRepo.git("checkout", "-b", defaultBranch);
    bareDestRepo.git("init", "--bare");
    bareDestRepo.git("symbolic-ref", "HEAD", "refs/heads/" + defaultBranch);

    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "credentials", "", "username", "test-password");
    store.addCredentials(Domain.global(), credentials);
  }

  @Test
  public void roundTrip() throws Exception {
    RepositorySyncBuilder builder = new RepositorySyncBuilder("https://git.cloudmanager.adobe.com/dummyrepo/dummyrepo", "weretail-credentials");
    RepositorySyncBuilder roundtrip = rule.configRoundtrip(builder);
    rule.assertEqualDataBoundBeans(builder, roundtrip);
  }

  @Test
  public void noSCMFailure() throws Exception {
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    acmRepoSync(url: $/" + srcRepo + "/$, credentialsId: 'credentials')\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertNotNull(run);
    assertTrue(StringUtils.contains(run.getLog(), Messages.RepositorySyncBuilder_error_missingGitRepository()));
  }

  @Test
  public void noLocalGitRepo() throws Exception {
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    dir('subdir') {\n" +
            "      acmRepoSync(url: $/" + destRepo + "/$, credentialsId: 'credentials')\n" +
            "    }\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertTrue(StringUtils.contains(run.getLog(), Messages.RepositorySyncBuilder_error_missingGitRepository()));
  }

  @Test
  public void conflictNoForce() throws Exception {
    srcRepo.write("newfile", "filecontents");
    srcRepo.git("add", "newfile");
    srcRepo.git("commit", "--message=file");

    destRepo.git("clone", bareDestRepo.toString(), ".");
    destRepo.git("checkout", "-b", defaultBranch);
    destRepo.git("config", "user.name", "Git SampleRepoRule");
    destRepo.git("config", "user.email", "gits@mplereporule");
    destRepo.write("testfile", "testfilecontents");
    destRepo.git("add", "testfile");
    destRepo.git("commit", "--message=testfile");
    destRepo.git("push");
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials')\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertTrue(StringUtils.contains(run.getLog(), "Push to Cloud Manager remote failed"));
  }

  @Test
  public void conflictWithForce() throws Exception {
    srcRepo.write("newfile", "filecontents");
    srcRepo.git("add", "newfile");
    srcRepo.git("commit", "--message=file");

    destRepo.git("clone", bareDestRepo.toString(), ".");
    destRepo.git("checkout", "-b", defaultBranch);
    destRepo.git("config", "user.name", "Git SampleRepoRule");
    destRepo.git("config", "user.email", "gits@mplereporule");
    destRepo.write("testfile", "testfilecontents");
    destRepo.git("add", "testfile");
    destRepo.git("commit", "--message=testfile");
    destRepo.git("push");
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials', force: true)\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    updatedDestRepo.git("clone", bareDestRepo.toString(), ".");
    assertEquals(srcRepo.head(), updatedDestRepo.head());

  }

  @Test
  public void missingTargetBranch() throws Exception {
    srcRepo.git("checkout", "-b", "notdefault");
    srcRepo.write("newfile", "filecontents");
    srcRepo.git("add", "newfile");
    srcRepo.git("commit", "--message=file");

    destRepo.git("clone", bareDestRepo.toString(), ".");
    destRepo.git("config", "user.name", "Git SampleRepoRule");
    destRepo.git("config", "user.email", "gits@mplereporule");
    destRepo.write("testfile", "testfilecontents");
    destRepo.git("add", "testfile");
    destRepo.git("commit", "--message=testfile");
    destRepo.git("push");
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: 'notdefault')\n" +
            "    acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials')\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    destRepo.git("fetch");
    destRepo.git("checkout", "notdefault");
    assertEquals(srcRepo.head(), destRepo.head());
  }

  @Test
  public void multipleScmsWarning() throws Exception {
    destRepo.init();

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    git(url: $/" + destRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials')\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    String log = run.getLog();
    assertTrue(StringUtils.contains(log, Messages.RepositorySyncBuilder_warning_multipleScms(srcRepo.toString())));
  }

  @Test
  public void scriptSuccess() throws Exception {
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('runner') {\n" +
            "  ws {\n" +
            "    git(url: $/" + srcRepo + "/$, branch: '" + defaultBranch + "')\n" +
            "    acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials')\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    destRepo.git("clone", bareDestRepo.toString(), ".");
    assertEquals(srcRepo.head(), destRepo.head());
  }

  @Test
  public void pipelineSuccess() throws Exception {

    String pipeline = "" +
        "pipeline {\n" +
        "    agent { label 'runner' }\n" +
        "    stages {\n" +
        "        stage('sync') {\n" +
        "            steps {\n" +
        "                acmRepoSync(url: $/" + bareDestRepo + "/$, credentialsId: 'credentials')\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "}\n";
    srcRepo.write("Jenkinsfile", pipeline);
    srcRepo.git("add", "Jenkinsfile");
    srcRepo.git("commit", "--message=Jenkinsfile");

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    SCM scm = new GitSCM(srcRepo.toString());
    CpsScmFlowDefinition flow = new CpsScmFlowDefinition(scm, "Jenkinsfile");
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    destRepo.git("clone", bareDestRepo.toString(), ".");
    assertEquals(srcRepo.head(), destRepo.head());
  }

  @Test
  public void pipelineCredentialed() throws Exception {
    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    server.addConnector(connector);
    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    handler.setContextPath("/*");
    ServletHolder holder = new ServletHolder(new DefaultServlet());
    handler.addServlet(holder, "/*");
    server.setHandler(handler);
    server.start();

    String host = connector.getHost() == null ? "localhost" : connector.getHost();
    String repoUrl = "http://" + host + ":" + connector.getLocalPort() + "/repo";

    String pipeline = "" +
        "pipeline {\n" +
        "    agent { label 'master' }\n" +
        "    stages {\n" +
        "        stage('sync') {\n" +
        "            steps {\n" +
        "                acmRepoSync(url: '" + repoUrl + "', credentialsId: 'credentials')\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "}\n";
    srcRepo.write("Jenkinsfile", pipeline);
    srcRepo.git("add", "Jenkinsfile");
    srcRepo.git("commit", "--message=Jenkinsfile");

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.createOnlineSlave(Label.get("runner"));
    SCM scm = new GitSCM(srcRepo.toString());
    CpsScmFlowDefinition flow = new CpsScmFlowDefinition(scm, "Jenkinsfile");
    job.setDefinition(flow);

    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
    assertTrue(StringUtils.contains(run.getLog(), "using GIT_ASKPASS to set credentials "));
  }

  @Test
  public void asBuildStep() throws Exception {

    FreeStyleProject project = rule.createProject(FreeStyleProject.class, "test");
    Builder builder = new RepositorySyncBuilder(bareDestRepo.toString(), "credentials");
    project.getBuildersList().add(builder);

    project.setScm(new GitSCM(srcRepo.toString()));

    FreeStyleBuild run = project.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    destRepo.git("clone", bareDestRepo.toString(), ".");
    assertEquals(srcRepo.head(), destRepo.head());
  }
}

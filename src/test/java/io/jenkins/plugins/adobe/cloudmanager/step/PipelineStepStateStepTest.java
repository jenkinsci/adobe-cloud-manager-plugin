package io.jenkins.plugins.adobe.cloudmanager.step;

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

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.htmlunit.html.HtmlPage;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineWaitingAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import io.jenkins.plugins.adobe.cloudmanager.test.RestartTest;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineStepStateStepTest {

  @ClassRule
  public static BuildWatcher buildWatcher = new BuildWatcher();

  @Rule
  public RestartableJenkinsRule story = new RestartableJenkinsRule();
  @Mocked
  private PipelineExecution pipelineExecution;
  @Mocked
  private PipelineExecutionStepState buildRunning;
  @Mocked
  private PipelineExecutionStepState buildFinished;
  @Mocked
  private PipelineExecutionStepState codeQualityWaiting;
  @Mocked
  private PipelineExecutionStepState codeQualityFinished;
  @Mocked
  private PipelineExecutionStepState approvalWaiting;
  @Mocked
  private PipelineExecutionStepState approvalFinished;

  @Mocked
  private AdobeIOProjectConfig projectConfig;
  @Mocked
  private CloudManagerApi api;

  @Before
  public void before() {
    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return projectConfig;
      }
    };
    new MockUp<CloudManagerApi>() {
      @Mock
      public CloudManagerApi create(String org, String apiKey, String token) {
        return api;
      }
    };

  }

  private void setupExpectations() {
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = "1";
      minTimes = 1;
      pipelineExecution.getPipelineId();
      result = "1";
      minTimes = 1;
      pipelineExecution.getId();
      result = "1";
      minTimes = 1;

      buildRunning.getAction();
      result = StepAction.build.toString();
      minTimes = 1;
      buildRunning.getStatusState();
      result = PipelineExecutionStepState.Status.RUNNING;
      minTimes = 1;

      buildFinished.getAction();
      result = StepAction.build.toString();
      minTimes = 1;
      buildFinished.getStatusState();
      result = PipelineExecutionStepState.Status.FINISHED;
      minTimes = 1;
      buildFinished.hasLogs();
      result = true;
      minTimes = 1;

      codeQualityWaiting.getAction();
      result = StepAction.codeQuality.toString();
      minTimes = 1;
      codeQualityWaiting.getStatusState();
      result = PipelineExecutionStepState.Status.WAITING;
      minTimes = 1;
      codeQualityWaiting.hasLogs();
      result = true;
      minTimes = 1;

      codeQualityFinished.getAction();
      result = StepAction.codeQuality.toString();
      minTimes = 1;
      codeQualityFinished.getStatusState();
      result = PipelineExecutionStepState.Status.FINISHED;
      minTimes = 1;
      codeQualityFinished.hasLogs();
      result = true;
      minTimes = 1;

      approvalWaiting.getAction();
      result = StepAction.approval.toString();
      minTimes = 1;
      approvalWaiting.getStatusState();
      result = PipelineExecutionStepState.Status.WAITING;
      minTimes = 1;
      approvalWaiting.hasLogs();
      result = true;
      minTimes = 1;

      approvalFinished.getAction();
      result = StepAction.approval.toString();
      minTimes = 1;
      approvalFinished.getStatusState();
      result = PipelineExecutionStepState.Status.FINISHED;
      minTimes = 1;
      approvalFinished.hasLogs();
      result = true;
      minTimes = 1;
    }};
  }

  private WorkflowRun setupRun(JenkinsRule rule) throws Exception {
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node {\n" +
            "    semaphore 'before'\n" +
            "    acmPipelineStepState()\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("before/1", run);
    run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
    SemaphoreStep.success("before/1", true);
    rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
    return run;
  }

  @Test
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "noBuildData");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).get();
      rule.waitForMessage(Messages.AbstractStepExecution_error_missingBuildData(), run);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void runningNotificationEvent() {

    story.then(rule -> {
      setupExpectations();

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, buildRunning);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "build", "RUNNING"), run);
      execution.process(pipelineExecution, buildFinished);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED")));
      CloudManagerBuildAction data = run.getAction(CloudManagerBuildAction.class);
      assertEquals(2, data.getSteps().size());
      CloudManagerBuildAction.PipelineStep expected = new CloudManagerBuildAction.PipelineStep(StepAction.valueOf("build"), PipelineExecutionStepState.Status.valueOf("RUNNING"), false);
      assertEquals(expected, data.getSteps().get(0));
      assertFalse(data.getSteps().get(0).isHasLogs());
    });
  }

  @Test
  public void finishedNotificationEvent() {

    story.then(rule -> {
      setupExpectations();
      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, buildFinished);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED")));
      CloudManagerBuildAction data = run.getAction(CloudManagerBuildAction.class);
      assertEquals(1, data.getSteps().size());
      CloudManagerBuildAction.PipelineStep expected = new CloudManagerBuildAction.PipelineStep(StepAction.valueOf("build"), PipelineExecutionStepState.Status.valueOf("FINISHED"), true);
      assertEquals(expected, data.getSteps().get(0));
      assertTrue(data.getSteps().get(0).isHasLogs());
    });
  }

  @Test
  public void finishedNotificationAdvanceFalseDoesNotAdvancePipeline() {
    story.then(rule -> {
      setupExpectations();
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState(advance: false)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, buildFinished);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED"), run);
      execution.process(pipelineExecution, codeQualityFinished);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "FINISHED"), run);

      CloudManagerBuildAction data = run.getAction(CloudManagerBuildAction.class);
      assertEquals(2, data.getSteps().size());
    });
  }

  @Test
  @Category(RestartTest.class)
  public void notificationSurvivesRestart() {

    story.then(this::setupRun);

    story.then(rule -> {
      setupExpectations();
      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, buildFinished);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED")));
      // This will show a bug in the logic.
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_waitingApproval()));
    });
  }

  @Test
  @Category(RestartTest.class)
  public void notificationSurvivesRestartValidationFails() {

    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
      run.addAction(action);
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      run.removeAction(action);
      run.save();
    });

    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.AbstractStepExecution_error_missingBuildData()));
    });
  }

  @Test
  @Category(RestartTest.class)
  public void notificationHandlesAbort() {
    story.then(this::setupRun);
    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      Executor executor;
      while ((executor = run.getExecutor()) == null) {
        Thread.sleep(100); // probably a race condition: AfterRestartTask could take a moment to be registered
      }
      assertNotNull(executor);
      executor.interrupt();
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.ABORTED, run);
    });
  }

  @Test
  public void waitingAutoApprove() {
    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState(autoApprove: true)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_autoApprove(), run);
      execution.process(pipelineExecution, codeQualityFinished);
      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(0, pauses.size());
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
      rule.assertBuildStatusSuccess(run);
    });
  }

  @Test
  public void waitingCodeQualityProceed() {
    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      execution.process(pipelineExecution, codeQualityFinished);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "FINISHED"), run);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingApprovalProceed() {

    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, approvalWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      execution.process(pipelineExecution, approvalFinished);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingProceedApiFailure() {
    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
        result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      waitingChecks(rule, run, Result.FAILURE);
    });
  }

  @Test
  public void waitingCodeQualityCancel(@Mocked PipelineExecutionStepState cancelled) {
    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        cancelled.getAction();
        result = StepAction.codeQuality.name();
        minTimes = 1;
        cancelled.getStatusState();
        result =  PipelineExecutionStepState.Status.CANCELLED;
        minTimes = 1;
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      execution.process(pipelineExecution, cancelled);
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingApprovalCancel(@Mocked PipelineExecutionStepState cancelled) {
    story.then(rule -> {
      setupExpectations();

      new Expectations() {{
        cancelled.getAction();
        result = StepAction.approval.name();
        minTimes = 1;
        cancelled.getStatusState();
        result = PipelineExecutionStepState.Status.CANCELLED;
        minTimes = 1;
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, approvalWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      execution.process(pipelineExecution, cancelled);
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingCancelApiFailure() {
    story.then(rule -> {
      setupExpectations();

      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
        result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      waitingChecks(rule, run, Result.FAILURE);
    });
  }

  @Test
  public void waitingIgnoresUnknownStepAction(@Mocked PipelineExecutionStepState unknown) {
    story.then(rule -> {
      setupExpectations();
      new Expectations() {{
        unknown.getAction();
        result = "Unknown";
        minTimes = 1;
        unknown.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
        minTimes = 1;
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, unknown);
      rule.waitForMessage(Messages.PipelineStepStateExecution_unknownStepAction("Unknown"), run);

      execution.process(pipelineExecution, buildFinished);
      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(0, pauses.size());
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_waiting()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
      rule.assertBuildStatusSuccess(run);
    });
  }

  @Test
  public void notificationAdvancesWaitingStep() {
    story.then(rule -> {
      setupExpectations();
      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      execution.process(pipelineExecution, buildFinished);

      waitingChecks(rule, run, Result.SUCCESS);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED")));
    });
  }

  @Test
  public void remoteEventAdvancesWaiting() {
    story.then(rule -> {
      setupExpectations();

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      execution.doEndQuietly();
      waitingChecks(rule, run, Result.SUCCESS);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_endQuietly()));
    });
  }

  @Test
  @Category(RestartTest.class)
  public void waitingSurvivesRestart() {
    story.then(rule -> {
      setupExpectations();
      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

    });

    story.then(rule -> {
      setupExpectations();
      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      CpsFlowExecution cfe = (CpsFlowExecution) run.getExecutionPromise().get();
      while (run.getAction(PipelineWaitingAction.class) == null) {
        cfe.waitForSuspension();
      }
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      rule.waitForMessage("Step was approved by", run);
      execution.process(pipelineExecution, codeQualityFinished);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "FINISHED"), run);
      rule.waitForCompletion(run);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  @Category(RestartTest.class)
  public void waitingHandlesAbort() {

    story.then(this::setupRun);
    story.then(rule -> {
      setupExpectations();

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);

      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      Executor executor;
      while ((executor = run.getExecutor()) == null) {
        Thread.sleep(100); // probably a race condition: AfterRestartTask could take a moment to be registered
      }
      assertNotNull(executor);
      executor.interrupt();
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  @Category(RestartTest.class)
  public void waitingSurvivesRestartValidationFails() {
    story.then(rule -> {
      setupExpectations();

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
      run.addAction(action);
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      run.removeAction(action);
      run.save();
    });

    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(1, pauses.size());
      assertTrue(pauses.get(0).isPaused()); // Can't cancel pause as validation happens outside scope.
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_waiting()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.AbstractStepExecution_error_missingBuildData()));
    });
  }

  @Test
  public void waitingPauseFalse() {
    story.then(rule -> {
      setupExpectations();
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState(waitingPause: false)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
      run.addAction(action);
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }

      execution.process(pipelineExecution, codeQualityWaiting);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("1", "codeQuality", "WAITING"), run);
      assertNull(execution.getReason());
      rule.waitForCompletion(run);
      rule.assertBuildStatusSuccess(run);
    });
  }

  @Test
  public void invalidStateAutoApproveAndNotWaiting() {
    story.then(rule -> {

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    acmPipelineStepState(autoApprove: true, waitingPause: false)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void handlesActionSubset() {
    story.then(rule -> {
      setupExpectations();
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState(actions: ['codeQuality', 'build', 'approval'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.process(pipelineExecution, buildFinished);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("1", "build", "FINISHED")));
    });
  }

  private void waitingChecks(JenkinsRule rule, WorkflowRun run, Result result) throws Exception {
    rule.waitForCompletion(run);
    List<PauseAction> pauses = new ArrayList<>();
    for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
      pauses.addAll(PauseAction.getPauseActions(n));
    }
    assertEquals(1, pauses.size());
    assertFalse(pauses.get(0).isPaused());
    assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_waiting()));
    assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
    assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
    String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
    assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
    rule.assertBuildStatus(result, run);
  }
}

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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
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
  private PipelineExecutionStepState stepState;
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
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        times = 2;
        result = StepAction.build.name();
        times = 2;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.RUNNING, PipelineExecutionStepState.Status.FINISHED);
        stepState.hasLogs();
        result = true;
        times = 2;
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.occurred(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "build", "RUNNING"), run);
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("ExecutionId", "build", "FINISHED")));
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
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
        stepState.hasLogs();
        result = true;
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("ExecutionId", "build", "FINISHED")));
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
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        returns(StepAction.validate.name(), StepAction.codeQuality.name());
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.FINISHED, PipelineExecutionStepState.Status.RUNNING);
        stepState.hasLogs();
        result = true;
      }};

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
      execution.occurred(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "validate", "FINISHED"), run);
      execution.occurred(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "RUNNING"), run);

      CloudManagerBuildAction data = run.getAction(CloudManagerBuildAction.class);
      assertEquals(2, data.getSteps().size());
    });
  }

  @Test
  @Category(RestartTest.class)
  public void notificationSurvivesRestart() {

    story.then(this::setupRun);

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
      }};

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("ExecutionId", "build", "FINISHED")));
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
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

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
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.codeQuality.name();
        times = 3;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.FINISHED);
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
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_autoApprove(), run);
      execution.occurred(pipelineExecution, stepState);
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
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.codeQuality.name();
        times = 3;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.FINISHED);
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      execution.occurred(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "FINISHED"), run);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingApprovalProceed() {

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.approval.name();
        times = 3;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.FINISHED);
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      execution.occurred(pipelineExecution, stepState);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingProceedApiFailure() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
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
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
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
  public void waitingCodeQualityCancel() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.codeQuality.name();
        times = 3;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.CANCELLED);
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      execution.occurred(pipelineExecution, stepState);
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingApprovalCancel() {
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.approval.name();
        times = 3;
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.CANCELLED);
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      execution.occurred(pipelineExecution, stepState);
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingCancelApiFailure() {
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
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
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
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
  public void waitingIgnoresUnknownStepAction() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        returns("Unknown", "Unknown", StepAction.deploy.name());
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_unknownStepAction("Unknown"), run);

      execution.occurred(pipelineExecution, stepState);
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
  public void waitingIgnoresNotWaitingStepAction() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        returns(StepAction.build.name(), StepAction.deploy.name());
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.FINISHED);
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);

      rule.waitForMessage(Messages.PipelineStepStateExecution_unknownWaitingAction("build"), run);

      execution.occurred(pipelineExecution, stepState);

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
  public void notificationAdvancesWaitingStep() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        returns(StepAction.codeQuality.name(), StepAction.deploy.name());
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.FINISHED);
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);
      execution.occurred(pipelineExecution, stepState);

      waitingChecks(rule, run, Result.SUCCESS);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("ExecutionId", "deploy", "FINISHED")));
    });
  }

  @Test
  public void remoteEventAdvancesWaiting() {
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule);

      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
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

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_waitingApproval(), run);

    });

    story.then(rule -> {
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
      }};
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
      execution.occurred(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "FINISHED"), run);
      rule.waitForCompletion(run);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }


  @Test
  @Category(RestartTest.class)
  public void waitingHandlesAbort() {

    story.then(this::setupRun);
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      PipelineStepStateExecution execution;
      while ((execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.waiting(pipelineExecution, stepState);

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
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

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
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
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
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

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

      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("ExecutionId", "codeQuality", "WAITING"), run);
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
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.FINISHED;
      }};

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
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_occurred("ExecutionId", "build", "FINISHED")));
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

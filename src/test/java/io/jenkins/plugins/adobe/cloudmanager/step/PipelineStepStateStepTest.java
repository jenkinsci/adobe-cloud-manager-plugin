package io.jenkins.plugins.adobe.cloudmanager.step;

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
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineWaitingAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
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
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
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

  private WorkflowRun setupRun(JenkinsRule rule, String test) throws Exception {
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, test);
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('master') {\n" +
            "    semaphore 'before'\n" +
            "    acmPipelineStepState()\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("before/1", run);
    run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
    SemaphoreStep.success("before/1", true);
    rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);
    return run;
  }

  @Test
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "noBuildData");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
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
  public void notificationEvent() {

    story.then(rule -> {
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

      WorkflowRun run = setupRun(rule, "notificationEvent");
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "build", "RUNNING")));
    });
  }

  @Test
  public void notificationSurvivesRestart() {

    final String test = "notificationSurvivesRestart";

    story.then(rule -> setupRun(rule, test));

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "build", "RUNNING")));
      // This will show a bug in the logic.
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_prompt_waitingApproval()));
    });
  }

  @Test
  public void notificationSurvivesRestartValidationFails() {
    final String test = "notificationSurvivesRestartValidationFails";

    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, test);
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1");
      run.addAction(action);
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);
      run.removeAction(action);
      run.save();
    });

    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.AbstractStepExecution_error_missingBuildData()));
    });
  }

  @Test
  public void waitingCodeQualityProceed() {
    final String test = "waitingCodeQualityProceed";
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
      }};

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingApprovalProceed() {

    final String test = "waitingApprovalProceed";

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.approval.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void waitingProceedApiFailure() {
    final String test = "waitingProceedApiFailure";
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

      WorkflowRun run = setupRun(rule, test);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      waitingChecks(rule, run, Result.FAILURE);
    });
  }

  @Test
  public void waitingCodeQualityCancel() {
    final String test = "waitingCodeQualityCancel";
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
      }};

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingApprovalCancel() {
    final String test = "waitingApprovalCancel";
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.approval.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.cancelExecution("1", "1", "1");
      }};

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "approval", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.approval, execution.getReason());

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      waitingChecks(rule, run, Result.ABORTED);
    });
  }

  @Test
  public void waitingCancelApiFailure() {
    final String test = "waitingCancelApiFailure";
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

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "cancel");
      waitingChecks(rule, run, Result.FAILURE);
    });
  }

  @Test
  public void waitingFailsOnUnknownStepAction() {
    final String test = "failsWaitingOnUnknownStepAction";
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = "Unknown";
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule, test);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
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
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_error_unknownStepAction("Unknown")));
    });
  }

  @Test
  public void waitingFailsOnNotWaitingStepAction() {
    final String test = "failsWaitingOnNotWaitingStepAction";
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule, test);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
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
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_error_unknownWaitingAction("build")));
    });
  }

  @Test
  public void notificationAdvancesWaitingStep() {
    final String test = "notificationAdvancesWaitingStep";
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;

        stepState.getAction();
        returns(StepAction.codeQuality.name(), StepAction.deploy.name());
        stepState.getStatusState();
        returns(PipelineExecutionStepState.Status.WAITING, PipelineExecutionStepState.Status.RUNNING);
      }};

      WorkflowRun run = setupRun(rule, test);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);
      execution.occurred(pipelineExecution, stepState);

      waitingChecks(rule, run, Result.SUCCESS);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "deploy", "RUNNING")));
    });
  }

  @Test
  public void remoteEventAdvancesWaiting() {
    final String test = "remoteEventAdvancesWaiting";
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule, test);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

      execution.doEndQuietly();
      waitingChecks(rule, run, Result.SUCCESS);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_info_endQuietly()));
    });
  }

  @Test
  public void waitingSurvivesRestart() {
    final String test = "waitingSurvivesRestart";
    story.then(rule -> {

      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = setupRun(rule, test);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);

    });

    story.then(rule -> {
      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.advanceExecution("1", "1", "1");
      }};
      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      CpsFlowExecution cfe = (CpsFlowExecution) run.getExecutionPromise().get();
      while (run.getAction(PipelineWaitingAction.class) == null) {
        cfe.waitForSuspension();
      }
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
      JenkinsRule.WebClient client = rule.createWebClient();
      HtmlPage page = client.getPage(run, action.getUrlName());
      rule.submit(page.getFormByName(execution.getId()), "proceed");
      rule.waitForCompletion(run);
      waitingChecks(rule, run, Result.SUCCESS);
    });
  }

  @Test
  public void notificationHandlesAbort() {
    final String test = "notificationHandlesAbort";
    story.then(rule -> setupRun(rule, test));

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);
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
  public void waitingHandlesAbort() {

    final String test = "waitingHandlesAbort";
    story.then(rule -> setupRun(rule, test));

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);

      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);
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
  public void waitingSurvivesRestartValidationFails() {
    final String test = "notificationSurvivesRestartValidationFails";

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.codeQuality.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.WAITING;
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, test);
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1");
      run.addAction(action);
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);

      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.waiting(pipelineExecution, stepState);
      rule.waitForMessage(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "codeQuality", "WAITING"), run);
      assertFalse(execution.isProcessed());
      assertEquals(StepAction.codeQuality, execution.getReason());
      rule.waitForMessage(Messages.PipelineStepStateExecution_prompt_waitingApproval(), run);
      run.removeAction(action);
      run.save();
    });

    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName(test, WorkflowJob.class).getBuildByNumber(1);
      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(1, pauses.size());
      assertTrue(pauses.get(0).isPaused()); // Can't cancel pause as validation happens outside scope.
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_info_waiting()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
      assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
      rule.assertBuildStatus(Result.FAILURE, run);
      assertTrue(run.getLog().contains(Messages.AbstractStepExecution_error_missingBuildData()));
    });
  }

  @Test
  public void handlesActionSubset() {
    final String test = "handlesActionSubset";
    story.then(rule -> {
      new Expectations() {{

        pipelineExecution.getId();
        result = "ExecutionId";
        times = 2;
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, test);
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState(actions: ['codeQuality', 'build', 'approval'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PipelineStepStateExecution_info_waiting(), run);
      PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution, stepState);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "build", "RUNNING")));
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
    assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_info_waiting()));
    assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_endPause()));
    assertFalse(run.getLog().contains(Messages.PipelineStepStateExecution_warn_actionRemoval()));
    String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
    assertFalse(xml.contains(PipelineStepStateExecution.class.getName()));
    rule.assertBuildStatus(result, run);
  }
}

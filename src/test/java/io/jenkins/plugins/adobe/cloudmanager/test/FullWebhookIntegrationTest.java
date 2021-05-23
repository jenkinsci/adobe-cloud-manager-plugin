package io.jenkins.plugins.adobe.cloudmanager.test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import hudson.model.Result;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepWaitingEvent;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineWaitingAction;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import io.jenkins.plugins.adobe.cloudmanager.webhook.CloudManagerWebHook;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import static io.adobe.cloudmanager.PipelineExecutionStepState.Status.*;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

/**
 * Full webhook test with a running pipeline.
 */
public class FullWebhookIntegrationTest {

  private static final String PROGRAM_ID = "1";
  private static final String PIPELINE_ID = "2";
  private static final String EXECUTION_ID = "3";
  @ClassRule
  public static BuildWatcher buildWatcher = new BuildWatcher();
  private static String PIPELINE_STARTED;
  private static String STEP_STARTED;
  private static String STEP_WAITING;
  private static String STEP_ENDED;
  private static String PIPELINE_ENDED;
  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private PipelineExecution pipelineExecution;

  @Mocked
  private PipelineExecutionStepState pipelineExecutionStepState;

  @BeforeClass
  public static void beforeClass() throws Exception {
    PIPELINE_STARTED = IOUtils.resourceToString("events/pipeline-started.json", StandardCharsets.UTF_8, FullWebhookIntegrationTest.class.getClassLoader());
    STEP_STARTED = IOUtils.resourceToString("events/step-started.json", StandardCharsets.UTF_8, FullWebhookIntegrationTest.class.getClassLoader());
    STEP_WAITING = IOUtils.resourceToString("events/step-waiting.json", StandardCharsets.UTF_8, FullWebhookIntegrationTest.class.getClassLoader());
    STEP_ENDED = IOUtils.resourceToString("events/step-ended.json", StandardCharsets.UTF_8, FullWebhookIntegrationTest.class.getClassLoader());
    PIPELINE_ENDED = IOUtils.resourceToString("events/pipeline-ended.json", StandardCharsets.UTF_8, FullWebhookIntegrationTest.class.getClassLoader());
  }

  private static void sendEvent(JenkinsRule rule, String payload) throws Exception {

    String url = String.format("%s%s/", rule.getURL().toString(), CloudManagerWebHook.URL_NAME);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.POST);
    con.setRequestProperty(CloudManagerEvent.SIGNATURE_HEADER, sign(payload));
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
    con.setDoOutput(true);
    IOUtils.write(payload, con.getOutputStream(), Charset.defaultCharset());
    assertEquals(HttpServletResponse.SC_OK, con.getResponseCode());
  }

  private static String sign(String toSign) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
  }

  private static void advanceWaiting(JenkinsRule rule, WorkflowRun run, Boolean approve) throws Exception {

    PipelineStepStateExecution execution = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
    PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
    String operation = approve ? "proceed" : "cancel";
    String url = String.format("%s%s%s/%s/%s", rule.getURL(), run.getUrl(), action.getUrlName(), execution.getId(), operation);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.POST);
    con.setRequestProperty("Jenkins-Crumb", "test");
    con.connect();
    assertEquals(HttpURLConnection.HTTP_OK, con.getResponseCode());
  }

  @Test
  public void testStartTrigger() throws Exception {

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (name) -> Optional.of(api);
      }
    };

    new Expectations() {{
      pipelineExecution.getProgramId();
      result = PROGRAM_ID;
      minTimes = 1;
      pipelineExecution.getPipelineId();
      result = PIPELINE_ID;
      minTimes = 1;
      pipelineExecution.getId();
      result = EXECUTION_ID;
      api.getExecution(withInstanceOf(PipelineExecutionStartEvent.class));
      result = pipelineExecution;
    }};

    WorkflowJob job = rule.createProject(WorkflowJob.class, "full");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "pipeline {\n" +
            "  agent any\n" +
            "  triggers { acmPipelineStart(aioProject: '" + AIO_PROJECT_NAME + "', program: '1', pipeline: '2') }\n" +
            "  stages {\n" +
            "    stage('Started') {\n" +
            "      steps {\n" +
            "        echo 'PipelineStartTrigger worked.'\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForCompletion(run);
    rule.assertBuildStatusSuccess(run);

    sendEvent(rule, PIPELINE_STARTED);
    while ((run = rule.jenkins.getItemByFullName("full", WorkflowJob.class).getBuildByNumber(2)) == null) {
      Thread.sleep(1000);
    }
    rule.waitForMessage("PipelineStartTrigger worked.", run);
    rule.waitForCompletion(run);
    rule.assertBuildStatusSuccess(run);
    CloudManagerBuildAction action = run.getAction(CloudManagerBuildAction.class);
    assertNotNull(action);
    assertEquals(PROGRAM_ID, action.getCmExecution().getProgramId());
    assertEquals(PIPELINE_ID, action.getCmExecution().getPipelineId());
    assertEquals(EXECUTION_ID, action.getCmExecution().getExecutionId());

  }

  @Test
  public void testApproved() throws Exception {

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (name) -> Optional.of(api);
      }
    };

    new Expectations() {{
      api.startExecution(PROGRAM_ID, PIPELINE_ID);
      result = pipelineExecution;
      pipelineExecution.getProgramId();
      result = PROGRAM_ID;
      minTimes = 1;
      pipelineExecution.getPipelineId();
      result = PIPELINE_ID;
      minTimes = 1;
      pipelineExecution.getId();
      result = EXECUTION_ID;
      api.getExecutionStepState(withInstanceOf(PipelineExecutionStepStartEvent.class));
      result = pipelineExecutionStepState;
      api.getExecutionStepState(withInstanceOf(PipelineExecutionStepWaitingEvent.class));
      result = pipelineExecutionStepState;
      api.getExecutionStepState(withInstanceOf(PipelineExecutionStepEndEvent.class));
      result = pipelineExecutionStepState;
      api.getExecution(withInstanceOf(PipelineExecutionEndEvent.class));
      result = pipelineExecution;
      pipelineExecution.getStatusState();
      result = PipelineExecution.Status.FINISHED;
      pipelineExecutionStepState.getAction();
      returns("codeQuality", "codeQuality", "codeQuality");
      pipelineExecutionStepState.getStatusState();
      returns(RUNNING, WAITING, FINISHED);
    }};

    WorkflowJob job = rule.createProject(WorkflowJob.class, "full");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('master') {\n" +
            "  acmStartPipeline aioProject: '" + AIO_PROJECT_NAME + "', program: '" + PROGRAM_ID + "', pipeline: '" + PIPELINE_ID + "'\n" +
            "  acmPipelineEnd() {\n" +
            "    parallel acm: {\n" +
            "      acmPipelineStepState()\n" +
            "    }, sema: {\n" +
            "      semaphore 'waiter'\n" +
            "    }\n" +
            "  }\n" +
            "}",
        true
    );
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForMessage("Start Adobe Cloud Manager Builder - Execution with id 3 started for pipeline 2.", run);
    rule.waitForMessage("Cloud Manager Pipeline End Step - Waiting for an event.", run);
    rule.waitForMessage("Cloud Manager Pipeline Step Execution - Waiting for an event.", run);

    SemaphoreStep.waitForStart("waiter/1", run);
    sendEvent(rule, STEP_STARTED);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, RUNNING), run);
    sendEvent(rule, STEP_WAITING);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, WAITING), run);
    advanceWaiting(rule, run, true);
    rule.waitForMessage(Messages.PipelineStepStateExecution_approvedBy("anonymous"), run);
    sendEvent(rule, STEP_ENDED);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, FINISHED), run);
    SemaphoreStep.success("waiter/1", true);

    SemaphoreStep.waitForStart("waiter/2", run);
    sendEvent(rule, PIPELINE_ENDED);
    SemaphoreStep.success("waiter/2", true);

    rule.waitForCompletion(run);
    rule.assertBuildStatusSuccess(run);
  }

  @Test
  public void testCanceled() throws Exception {

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (name) -> Optional.of(api);
      }
    };

    new Expectations() {{
      api.startExecution(PROGRAM_ID, PIPELINE_ID);
      result = pipelineExecution;
      pipelineExecution.getProgramId();
      result = PROGRAM_ID;
      minTimes = 1;
      pipelineExecution.getPipelineId();
      result = PIPELINE_ID;
      minTimes = 1;
      pipelineExecution.getId();
      result = EXECUTION_ID;
      api.getExecutionStepState(withInstanceOf(PipelineExecutionStepStartEvent.class));
      result = pipelineExecutionStepState;
      api.getExecutionStepState(withInstanceOf(PipelineExecutionStepWaitingEvent.class));
      result = pipelineExecutionStepState;
      api.getExecution(withInstanceOf(PipelineExecutionEndEvent.class));
      result = pipelineExecution;
      pipelineExecution.getStatusState();
      result = PipelineExecution.Status.ERROR;
      pipelineExecutionStepState.getAction();
      returns("codeQuality", "codeQuality", "codeQuality");
      pipelineExecutionStepState.getStatusState();
      returns(RUNNING, WAITING, FINISHED);
    }};

    WorkflowJob job = rule.createProject(WorkflowJob.class, "full");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node('master') {\n" +
            "  acmStartPipeline aioProject: '" + AIO_PROJECT_NAME + "', program: '" + PROGRAM_ID + "', pipeline: '" + PIPELINE_ID + "'\n" +
            "  acmPipelineEnd() {\n" +
            "    parallel acm: {\n" +
            "      acmPipelineStepState()\n" +
            "    }, sema: {\n" +
            "      semaphore 'waiter'\n" +
            "    }\n" +
            "  }\n" +
            "}",
        true
    );
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    rule.waitForMessage("Start Adobe Cloud Manager Builder - Execution with id 3 started for pipeline 2.", run);
    rule.waitForMessage("Cloud Manager Pipeline End Step - Waiting for an event.", run);
    rule.waitForMessage("Cloud Manager Pipeline Step Execution - Waiting for an event.", run);

    SemaphoreStep.waitForStart("waiter/1", run);
    sendEvent(rule, STEP_STARTED);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, RUNNING), run);
    sendEvent(rule, STEP_WAITING);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, WAITING), run);
    advanceWaiting(rule, run, true);
    rule.waitForMessage(Messages.PipelineStepStateExecution_approvedBy("anonymous"), run);
    sendEvent(rule, STEP_ENDED);
    rule.waitForMessage(Messages.PipelineStepStateExecution_occurred("3", StepAction.codeQuality, FINISHED), run);
    SemaphoreStep.success("waiter/1", true);

    SemaphoreStep.waitForStart("waiter/2", run);
    sendEvent(rule, PIPELINE_ENDED);
    SemaphoreStep.success("waiter/2", true);

    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.FAILURE, run);
  }
}


package io.jenkins.plugins.adobe.cloudmanager.trigger;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Function;

import hudson.Util;
import hudson.model.Result;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineStartTriggerTest {

  private static final String PROGRAM_NAME = "Program Name";
  private static final String PIPELINE_NAME = "Pipeline Name";
  private static final String EVENT_ID = "Event ID";

  @ClassRule
  public static BuildWatcher watcher = new BuildWatcher();

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private Program program;

  @Mocked
  private Pipeline pipeline;

  @Before
  public void before() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (project) -> Optional.of(api);
      }
    };
  }

  @Test
  public void matchesRules() throws Exception {

    PipelineStartEvent event = new PipelineStartEvent(EVENT_ID, AIO_PROJECT_NAME, "1", "2", "3", OffsetDateTime.now());

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    PipelineStartTrigger trigger = new PipelineStartTrigger(AIO_PROJECT_NAME, "1", "2");
    job.addTrigger(trigger);
    assertTrue(PipelineStartTrigger.interestedIn(event).test(trigger));
  }

  @Test
  public void startsJob() throws Exception {
    PipelineStartEvent event = new PipelineStartEvent(EVENT_ID, AIO_PROJECT_NAME, "1", "2", "3", OffsetDateTime.now());

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
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
    rule.assertBuildStatus(Result.SUCCESS, run);

    for (PipelineStartTrigger t : Util.filter(job.getTriggers().values(), PipelineStartTrigger.class)) {
      t.onEvent(event);
    }
    while ((run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(2)) == null) {
      Thread.sleep(1000);
    }
    rule.waitForMessage("PipelineStartTrigger worked.", run);
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    CloudManagerBuildAction action = run.getAction(CloudManagerBuildAction.class);
    assertNotNull(action);
    assertEquals("1", action.getCmExecution().getProgramId());
    assertEquals("2", action.getCmExecution().getPipelineId());
    assertEquals("3", action.getCmExecution().getExecutionId());
  }

  @Test
  public void startsJobUsingNames() throws Exception {

    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Optional<String> getProgramId(CloudManagerApi api, String programName) {
        return Optional.of("1");
      }
      @Mock
      public Optional<String> getPipelineId(CloudManagerApi api, String programId, String pipelineName) {
        return Optional.of("2");
      }
    };

    PipelineStartEvent event = new PipelineStartEvent(EVENT_ID, AIO_PROJECT_NAME, "1", "2", "3", OffsetDateTime.now());

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "pipeline {\n" +
            "  agent any\n" +
            "  triggers { acmPipelineStart(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + PROGRAM_NAME + "', pipeline: '" + PIPELINE_NAME + "') }\n" +
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
    rule.assertBuildStatus(Result.SUCCESS, run);

    for (PipelineStartTrigger t : Util.filter(job.getTriggers().values(), PipelineStartTrigger.class)) {
      t.onEvent(event);
    }
    while ((run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(2)) == null) {
      Thread.sleep(1000);
    }
    rule.waitForMessage("PipelineStartTrigger worked.", run);
    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    CloudManagerBuildAction action = run.getAction(CloudManagerBuildAction.class);
    assertNotNull(action);
    assertEquals("1", action.getCmExecution().getProgramId());
    assertEquals("2", action.getCmExecution().getPipelineId());
    assertEquals("3", action.getCmExecution().getExecutionId());
  }


  @Test
  public void apiCreateFails() throws Exception {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() {
        return (project) -> Optional.empty();
      }
    };

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
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
    rule.assertBuildStatus(Result.FAILURE, run);
  }

  @Test
  public void programIdFails() throws Exception {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> getProgramId(CloudManagerApi api, String programName) {
        return (project) -> Optional.empty();
      }
    };

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "pipeline {\n" +
            "  agent any\n" +
            "  triggers { acmPipelineStart(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + PROGRAM_NAME + "', pipeline: '" + PIPELINE_NAME + "') }\n" +
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
    rule.assertBuildStatus(Result.FAILURE, run);
  }

  @Test
  public void pipelineIdFails() throws Exception {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> getPipelineId(CloudManagerApi api, String programId, String pipelineName) {
        return (project) -> Optional.empty();
      }
    };

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "pipeline {\n" +
            "  agent any\n" +
            "  triggers { acmPipelineStart(aioProject: '" + AIO_PROJECT_NAME + "', program: '" + PROGRAM_NAME + "', pipeline: '" + PIPELINE_NAME + "') }\n" +
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
    rule.assertBuildStatus(Result.FAILURE, run);
  }
}

package io.jenkins.plugins.adobe.cloudmanager.step;

import hudson.model.Result;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;

public class AdvancePipelineStepTest {

  @ClassRule
  public static BuildWatcher buildWatcher = new BuildWatcher();

  @Rule
  public RestartableJenkinsRule story = new RestartableJenkinsRule();

  @Mocked
  private AdobeIOProjectConfig projectConfig;
  @Mocked
  private CloudManagerApi api;

  @Mocked
  private PipelineExecution pipelineExecution;
  @Mocked
  private PipelineExecutionStepState stepState;

  @Before
  public void before() {
    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return projectConfig;
      }
    };
  }

  @Test
  public void invalidAction() {
    story.then(rule ->  {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
           "    acmAdvancePipeline(actions:['build', 'codeQuality'])\n" +
          "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    acmAdvancePipeline()\n" +
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
  public void advancesCodeQuality() {
    story.then(rule ->  {

      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.getExecution("1", "1", "1");
        result = pipelineExecution;
        api.getCurrentStep(pipelineExecution);
        result = stepState;
        stepState.getAction();
        result = "codeQuality";
        api.advanceExecution(pipelineExecution);
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmAdvancePipeline(actions:['codeQuality'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.AdvancePipelineExecution_info_advancingPipeline("codeQuality"), run);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
    });
  }

  @Test
  public void advancesApproval() {
    story.then(rule ->  {

      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.getExecution("1", "1", "1");
        result = pipelineExecution;
        api.getCurrentStep(pipelineExecution);
        result = stepState;
        stepState.getAction();
        result = "approval";
        api.advanceExecution(pipelineExecution);
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmAdvancePipeline(actions:['approval'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.AdvancePipelineExecution_info_advancingPipeline("approval"), run);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
    });
  }

  @Test
  public void advanceApiFailure() {
    story.then(rule ->  {

      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.getExecution("1", "1", "1");
        result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmAdvancePipeline(actions:['codeQuality'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void advanceWrongState() {
    story.then(rule ->  {

      new Expectations() {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.getExecution("1", "1", "1");
        result = pipelineExecution;
        api.getCurrentStep(pipelineExecution);
        result = stepState;
        stepState.getAction();
        result = "approval";
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmAdvancePipeline(actions:['codeQuality'])\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

}

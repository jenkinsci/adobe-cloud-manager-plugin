package io.jenkins.plugins.adobe.cloudmanager.step;

import java.io.IOException;

import hudson.model.Result;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerBuildData;
import mockit.Expectations;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static io.adobe.cloudmanager.CloudManagerEvent.Type.*;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineStepStateStepTest {

  @Mocked
  private PipelineExecution pipelineExecution;

  @Mocked
  private PipelineExecutionStepState stepState;

  @Rule
  public RestartableJenkinsRule story = new RestartableJenkinsRule();

  @Test
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
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
  public void startedEvent() {

    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        stepState.getAction();
        result = StepAction.build.name();
        stepState.getStatusState();
        result = PipelineExecutionStepState.Status.RUNNING;
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineStepState()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildData(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      CpsFlowExecution cfe = (CpsFlowExecution) run.getExecutionPromise().get();
      while (run.getAction(PipelineStepStateAction.class) == null) {
        cfe.waitForSuspension();
      }
      // Now we're waiting for input.
      PipelineStepStateAction action = run.getAction(PipelineStepStateAction.class);
      assertEquals(1, action.getExecutions().size());

      // Poke the Execution to make it move.
      StepExecution.applyAll(PipelineStepStateExecution.class, execution -> {
        try {
          execution.occurred(pipelineExecution, stepState);
        } catch (IOException | InterruptedException e) {
          // do nothing
        }
        return null;
      }).get();
      rule.waitForCompletion(run);
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_info_waiting()));
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_event_occurred("ExecutionId", "build", "RUNNING")));
    });
  }

  @Test
  public void waitingEvent() {
    fail("Not Implemented");
  }

  @Test
  public void endedEvent() {
    fail("Not Implemented");
  }
}

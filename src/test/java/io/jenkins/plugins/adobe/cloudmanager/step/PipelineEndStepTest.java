package io.jenkins.plugins.adobe.cloudmanager.step;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;

import hudson.Extension;
import hudson.model.Result;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.PipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineWaitingAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import mockit.Expectations;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineEndStepTest {

  @ClassRule
  public static BuildWatcher buildWatcher = new BuildWatcher();
  @Rule
  public RestartableJenkinsRule story = new RestartableJenkinsRule();
  @Mocked
  private PipelineExecution pipelineExecution;
  @Mocked
  private AdobeIOProjectConfig projectConfig;
  @Mocked
  private CloudManagerApi api;

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
  public void mirrorsFinishedSuccess() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.FINISHED;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "        semaphore 'inside'\n" +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);

      SemaphoreStep.waitForStart("inside/1", run);
      // Poke the Execution to make it move.
      PipelineEndExecution execution = (PipelineEndExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution);
      SemaphoreStep.success("inside/1", run);
      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(0, pauses.size());
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_event_occurred("ExecutionId", "FINISHED")));

    });
  }

  @Test
  public void quietlyEndsStateSteps() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.FINISHED;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "        acmPipelineStepState()\n " +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);

      CpsFlowExecution cfe = (CpsFlowExecution) run.getExecutionPromise().get();
      while (run.getAction(PipelineWaitingAction.class) == null) {
        cfe.waitForSuspension();
      }

      // Poke the Execution to make it end.
      StepExecution.applyAll(PipelineEndExecution.class, execution -> {
        try {
          execution.occurred(pipelineExecution);
        } catch (IOException | InterruptedException e) {
          // do nothing
        }
        return null;
      }).get();

      rule.waitForCompletion(run);
      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(1, pauses.size());
      assertFalse(pauses.get(0).isPaused());

      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_event_occurred("ExecutionId", "FINISHED")));

    });
  }

  @Test
  public void quietlyEndsStateStepButStillBlocked() throws Exception {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.FINISHED;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "      parallel stopped: {\n" +
              "        acmPipelineStepState()\n " +
              "      }, waiting: {\n" +
              "        waitUntil(initialRecurrencePeriod: 1000) {\n" +
              "          testStep()\n" +
              "        }\n" +
              "      }\n" +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);

      CpsFlowExecution cfe = (CpsFlowExecution) run.getExecutionPromise().get();
      while (run.getAction(PipelineWaitingAction.class) == null) {
        cfe.waitForSuspension();
      }
      // Poke the Execution to make it end.
      List<StepExecution> executions = run.getExecution().getCurrentExecutions(false).get();
      PipelineEndExecution ex = (PipelineEndExecution) executions.stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null);
      ex.occurred(pipelineExecution);

      PipelineStepStateExecution psse = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      assertNull(psse);
      TestStep.finished = true;

      List<PauseAction> pauses = new ArrayList<>();
      for (FlowNode n : new FlowGraphWalker(run.getExecution())) {
        pauses.addAll(PauseAction.getPauseActions(n));
      }
      assertEquals(1, pauses.size());
      assertFalse(pauses.get(0).isPaused());

      rule.waitForCompletion(run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_info_waiting()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_event_occurred("ExecutionId", "FINISHED")));

    });
  }

  public static final class TestStep extends Step {

    public static boolean finished = false;
    @DataBoundConstructor
    public TestStep() {

    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
      return new Execution(context, this);
    }

    public static final class Execution extends StepExecution {

      public final TestStep step;
      public static int i = 0;
      public Execution(StepContext context, TestStep step) {
        super(context);
        this.step = step;
      }

      @Override
      public boolean start() throws Exception {
        getContext().onSuccess(step.finished);
        return false;
      }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
      @Override
      public Set<? extends Class<?>> getRequiredContext() {
        return Collections.emptySet();
      }

      @Override
      public String getFunctionName() {
        return "testStep";
      }
    }
  }
}

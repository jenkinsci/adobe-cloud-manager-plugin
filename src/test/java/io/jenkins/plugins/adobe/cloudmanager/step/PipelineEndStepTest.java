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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import hudson.Extension;
import hudson.model.Result;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.PipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;
import org.kohsuke.stapler.DataBoundConstructor;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
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
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
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

  private void mirrorRun(PipelineExecution.Status status, Result result) {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = status;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "        semaphore 'inside'\n" +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);

      SemaphoreStep.waitForStart("inside/1", run);
      PipelineEndExecution execution = (PipelineEndExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution);
      SemaphoreStep.success("inside/1", run);
      rule.waitForCompletion(run);
      rule.assertBuildStatus(result, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_occurred("ExecutionId", status.name())));
    });
  }

  @Test
  public void mirrorsFinishedSuccess() {
    mirrorRun(PipelineExecution.Status.FINISHED, Result.SUCCESS);
  }

  @Test
  public void mirrorsCancelledFails() {
    mirrorRun(PipelineExecution.Status.CANCELLED, Result.FAILURE);
  }

  @Test
  public void mirrorsErrorFails() {
    mirrorRun(PipelineExecution.Status.ERROR, Result.FAILURE);
  }

  @Test
  public void mirrorsFailureFails() {
    mirrorRun(PipelineExecution.Status.FAILED, Result.FAILURE);
  }

  @Test
  public void doesNotMirrorFailure() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.ERROR;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd(mirror: false) {\n" +
              "        semaphore 'inside'\n" +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);

      SemaphoreStep.waitForStart("inside/1", run);
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
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_occurred("ExecutionId", "ERROR")));
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
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "        acmPipelineStepState()\n " +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);

      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      PipelineEndExecution execution = (PipelineEndExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null);
      execution.occurred(pipelineExecution);

      rule.waitForCompletion(run);

      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_occurred("ExecutionId", "FINISHED")));
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_endQuietly()));

    });
  }

  @Test
  public void quietlyEndsStateStepButStillBlocked() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.FINISHED;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "      parallel stopped: {\n" +
              "        acmPipelineStepState()\n " +
              "      }, waiting: {\n" +
              "        waitUntil(initialRecurrencePeriod: 1000) {\n" +
              "          testRecurrenceStep()\n" +
              "        }\n" +
              "      }\n" +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);

      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
      // Poke the Execution to make it end.
      List<StepExecution> executions = run.getExecution().getCurrentExecutions(false).get();
      PipelineEndExecution ex = (PipelineEndExecution) executions.stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null);
      ex.occurred(pipelineExecution);

      rule.waitForMessage(Messages.PipelineStepStateExecution_endQuietly(), run);
      PipelineStepStateExecution psse = (PipelineStepStateExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineStepStateExecution).findFirst().orElse(null);
      assertNull(psse);
      rule.waitForMessage("Will try again after 1.2 sec", run);
      TestRecurrenceStep.finished = true;

      rule.waitForCompletion(run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_waiting()));
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_occurred("ExecutionId", "FINISHED")));
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_endQuietly()));
    });
  }

  @Test
  @WithTimeout(300)
  public void handlesRestartDuringBlock() {
    story.then(rule -> {
      new Expectations() {{
        pipelineExecution.getId();
        result = "ExecutionId";
        pipelineExecution.getStatusState();
        result = PipelineExecution.Status.FINISHED;
      }};
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node {\n" +
              "    semaphore 'before'\n" +
              "    acmPipelineEnd {\n" +
              "        acmPipelineStepState()\n " +
              "    }\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1")));
      SemaphoreStep.success("before/1", true);

      rule.waitForMessage(Messages.PipelineStepStateExecution_waiting(), run);
    });
    story.then(rule -> {

      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      PipelineEndExecution execution;
      while ((execution = (PipelineEndExecution) run.getExecution().getCurrentExecutions(false).get().stream().filter(e -> e instanceof PipelineEndExecution).findFirst().orElse(null)) == null) {
        Thread.sleep(100);
      }
      execution.occurred(pipelineExecution);

      rule.waitForCompletion(run);
      String xml = FileUtils.readFileToString(new File(run.getRootDir(), "build.xml"), Charset.defaultCharset());
      assertFalse(xml.contains(PipelineEndExecution.class.getName()));
      rule.assertBuildStatus(Result.SUCCESS, run);
      assertTrue(run.getLog().contains(Messages.PipelineEndExecution_occurred("ExecutionId", "FINISHED")));
      assertTrue(run.getLog().contains(Messages.PipelineStepStateExecution_endQuietly()));
    });
  }

  public static final class TestRecurrenceStep extends Step {

    public static boolean finished = false;

    @DataBoundConstructor
    public TestRecurrenceStep() {

    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
      return new Execution(context, this);
    }

    public static final class Execution extends StepExecution {
      public final TestRecurrenceStep step;

      public Execution(StepContext context, TestRecurrenceStep step) {
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
        return "testRecurrenceStep";
      }
    }
  }
}

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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nonnull;

import hudson.Util;
import hudson.model.Result;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.Messages;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PollPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerApiUtil;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PollPipelineStepTest {

  @ClassRule
  public static BuildWatcher watcher = new BuildWatcher();

  @Rule
  public RestartableJenkinsRule story = new RestartableJenkinsRule();

  @Mocked
  private AdobeIOProjectConfig projectConfig;

  @Mocked
  private CloudManagerApi api;

  @Before
  public void before() {
    new MockUp<CloudManagerApiUtil>() {
      @Mock
      public Function<String, Optional<CloudManagerApi>> createApi() { return (name) -> Optional.of(api); }
    };
  }

  @Test
  public void noBuildData() {
    story.then(rule -> {
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    acmPollPipeline()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).get();
      rule.waitForMessage(Messages.AbstractStepExecution_error_missingBuildData(), run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void buildDataMissingInContext() {
    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return null;
        }
      };
      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.AbstractStepExecution_error_missingBuildData(), run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void apiCreationFails() {

    story.then(rule -> {

      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new MockUp<CloudManagerApiUtil>() {
        @Mock
        public Function<String, Optional<CloudManagerApi>> createApi() { return (name) -> Optional.empty(); }
      };

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.AbstractStepExecution_error_missingBuildData(), run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void apiCheckFails() {

    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new Expectations(projectConfig) {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.isExecutionRunning("1", "1", "1");
        result = new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "1");
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage("An API exception occurred:", run);
      rule.assertBuildStatus(Result.FAILURE, run);
    });
  }

  @Test
  public void noWaitComplete() {

    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new Expectations(projectConfig) {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.isExecutionRunning("1", "1", "1");
        result = false;
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline()\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForCompletion(run);
      rule.assertBuildStatusSuccess(run);
    });
  }

  @Test
  public void recurrencePeriod() {

    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new Expectations(projectConfig) {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.isExecutionRunning("1", "1", "1");
        returns(true, false);
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline(recurrencePeriod: 10)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PollPipelineExecution_waiting(Util.getTimeSpanString(TimeUnit.SECONDS.toMillis(1))), run);
      rule.waitForCompletion(run);
      rule.assertBuildStatusSuccess(run);
    });
  }

  @Test
  public void survivesRestart() {

    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new Expectations(projectConfig) {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.isExecutionRunning("1", "1", "1");
        result = true;
      }};

      WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
      CpsFlowDefinition flow = new CpsFlowDefinition(
          "node('master') {\n" +
              "    semaphore 'before'\n" +
              "    acmPollPipeline(recurrencePeriod: 30)\n" +
              "}",
          true);
      job.setDefinition(flow);
      WorkflowRun run = job.scheduleBuild2(0).waitForStart();
      SemaphoreStep.waitForStart("before/1", run);
      run.addAction(new CloudManagerBuildAction(AIO_PROJECT_NAME, "1", "1", "1"));
      SemaphoreStep.success("before/1", true);
      rule.waitForMessage(Messages.PollPipelineExecution_waiting(Util.getTimeSpanString(TimeUnit.SECONDS.toMillis(30))), run);
      final List<PollPipelineExecution> executions = new ArrayList<>();
      StepExecution.applyAll(PollPipelineExecution.class, excution -> {
        executions.add(excution);
        return null;
      }).get();
      assertEquals(1, executions.size());
    });

    story.then(rule -> {
      new MockUp<AdobeIOConfig>() {
        @Mock
        public AdobeIOProjectConfig projectConfigFor(String name) {
          return projectConfig;
        }
      };

      new Expectations(projectConfig) {{
        projectConfig.authenticate();
        result = Secret.fromString(ACCESS_TOKEN);
        api.isExecutionRunning("1", "1", "1");
        result = false;
      }};
      WorkflowRun run = rule.jenkins.getItemByFullName("test", WorkflowJob.class).getBuildByNumber(1);
      rule.waitForCompletion(run);
      rule.assertBuildStatusSuccess(run);
    });
  }

  @TestExtension("recurrencePeriod")
  public static final class SetTimeoutStepListener implements StepListener {
    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext stepContext) {
      if (step instanceof PollPipelineStep) {
        PollPipelineStep pollStep = (PollPipelineStep) step;
        assertEquals(30000, pollStep.getRecurrencePeriod());
        try {
          Field rp = PollPipelineStep.class.getDeclaredField("recurrencePeriod");
          rp.setAccessible(true);
          rp.set(pollStep, 1000);
        } catch (Exception e) {
          fail(e.getLocalizedMessage());
        }

      }
    }
  }
}

package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.CloudManagerEvent.EventType;
import io.adobe.cloudmanager.event.PipelineExecutionStepEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepWaitingEvent;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineStepEventSubscriberTest {

  private static final String MESSAGE = "Waiting for event.";

  @ClassRule
  public static BuildWatcher watcher = new BuildWatcher();
  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Tested
  private PipelineStepEventSubscriber tested;

  @Mocked
  private AdobeIOProjectConfig projectConfig;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private PipelineExecutionStepState stepState;

  @Mocked
  private PipelineExecution pipelineExecution;

  @Test
  public void handlesStartEvent() throws Exception {
    String payload = IOUtils.resourceToString("events/step-started.json", Charset.defaultCharset(), PipelineStepEventSubscriberTest.class.getClassLoader());
    EventType type = EventType.from(payload);
    CloudManagerSubscriberEvent subscriberEvent = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, type, payload);
    PipelineExecutionStepStartEvent event = CloudManagerEvent.parseEvent(payload, PipelineExecutionStepStartEvent.class);

    new MockUp<CloudManagerApi>() {
      @Mock
      public CloudManagerApi create(String org, String apiKey, String token, String url) {
        return api;
      }
    };

    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return projectConfig;
      }
    };

    new Expectations() {{
      projectConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
      api.getExecutionStepState(event);
      result = stepState;
      stepState.getExecution();
      result = pipelineExecution;
    }};
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node {\n" +
            "    semaphore 'before'\n" +
            "    testStateSubscriberStep()\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("before/1", run);
    CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
    run.addAction(action);
    SemaphoreStep.success("before/1", true);
    rule.waitForMessage(MESSAGE, run);
    List<StepExecution> executions = run.getExecution().getCurrentExecutions(false).get();
    TestRecordEventStep.Execution ex = (TestRecordEventStep.Execution) executions.stream().filter(e -> e instanceof TestRecordEventStep.Execution).findFirst().orElse(null);

    tested.onEvent(subscriberEvent);

    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    assertEquals(stepState, ex.step.state);
  }

  @Test
  public void handlesWaitingEvent() throws Exception {
    String payload = IOUtils.resourceToString("events/step-waiting.json", Charset.defaultCharset(), PipelineStepEventSubscriberTest.class.getClassLoader());
    EventType type = EventType.from(payload);
    CloudManagerSubscriberEvent subscriberEvent = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, type, payload);
    PipelineExecutionStepWaitingEvent event = CloudManagerEvent.parseEvent(payload, PipelineExecutionStepWaitingEvent.class);

    new MockUp<CloudManagerApi>() {
      @Mock
      public CloudManagerApi create(String org, String apiKey, String token, String url) {
        return api;
      }
    };

    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return projectConfig;
      }
    };

    new Expectations() {{
      projectConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
      api.getExecutionStepState(event);
      result = stepState;
      stepState.getExecution();
      result = pipelineExecution;
    }};
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node {\n" +
            "    semaphore 'before'\n" +
            "    testStateSubscriberStep()\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("before/1", run);
    CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
    run.addAction(action);
    SemaphoreStep.success("before/1", true);
    rule.waitForMessage(MESSAGE, run);
    List<StepExecution> executions = run.getExecution().getCurrentExecutions(false).get();
    TestRecordEventStep.Execution ex = (TestRecordEventStep.Execution) executions.stream().filter(e -> e instanceof TestRecordEventStep.Execution).findFirst().orElse(null);

    tested.onEvent(subscriberEvent);

    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    assertEquals(stepState, ex.step.state);
  }

  @Test
  public void handlesEndEvent() throws Exception {
    String payload = IOUtils.resourceToString("events/step-ended.json", Charset.defaultCharset(), PipelineStepEventSubscriberTest.class.getClassLoader());
    EventType type = EventType.from(payload);
    CloudManagerSubscriberEvent subscriberEvent = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, type, payload);
    PipelineExecutionStepEndEvent event = CloudManagerEvent.parseEvent(payload, PipelineExecutionStepEndEvent.class);

    new MockUp<CloudManagerApi>() {
      @Mock
      public CloudManagerApi create(String org, String apiKey, String token, String url) {
        return api;
      }
    };

    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return projectConfig;
      }
    };

    new Expectations() {{
      projectConfig.authenticate();
      result = Secret.fromString(ACCESS_TOKEN);
      api.getExecutionStepState(event);
      result = stepState;
      stepState.getExecution();
      result = pipelineExecution;
    }};
    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    CpsFlowDefinition flow = new CpsFlowDefinition(
        "node {\n" +
            "    semaphore 'before'\n" +
            "    testStateSubscriberStep()\n" +
            "}",
        true);
    job.setDefinition(flow);
    WorkflowRun run = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("before/1", run);
    CloudManagerBuildAction action = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution("1", "1", "1"));
    run.addAction(action);
    SemaphoreStep.success("before/1", true);
    rule.waitForMessage(MESSAGE, run);
    List<StepExecution> executions = run.getExecution().getCurrentExecutions(false).get();
    TestRecordEventStep.Execution ex = (TestRecordEventStep.Execution) executions.stream().filter(e -> e instanceof TestRecordEventStep.Execution).findFirst().orElse(null);

    tested.onEvent(subscriberEvent);

    rule.waitForCompletion(run);
    rule.assertBuildStatus(Result.SUCCESS, run);
    assertEquals(stepState, ex.step.state);
  }

  public static final class TestRecordEventStep extends Step {

    public PipelineExecutionStepState state;

    @DataBoundConstructor
    public TestRecordEventStep() {

    }

    @Override
    public StepExecution start(StepContext context) {
      return new Execution(context, this);
    }

    public static final class Execution extends PipelineStepStateExecution {
      public transient TestRecordEventStep step;

      public Execution(StepContext context, TestRecordEventStep step) {
        super(context, Collections.emptySet(), false, true, true);
        this.step = step;
      }

      @Override
      public void doStart() throws Exception {
        getContext().get(TaskListener.class).getLogger().println(MESSAGE);
      }

      @Override
      public void process(PipelineExecution pe, PipelineExecutionStepState stepState) {
        step.state = stepState;
        getContext().onSuccess(null);
      }

      @Override
      public boolean isApplicable(PipelineExecutionStepState stepState) {
        return true;
      }

      @Override
      public boolean isApplicable(PipelineExecution pe) {
        return true;
      }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
      @Override
      public Set<? extends Class<?>> getRequiredContext() {
        return new HashSet<>(Arrays.asList(TaskListener.class));
      }

      @Override
      public String getFunctionName() {
        return "testStateSubscriberStep";
      }
    }
  }
}

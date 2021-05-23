package io.jenkins.plugins.adobe.cloudmanager.step.execution;

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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;

import hudson.model.TaskListener;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class PipelineStepStateExecutionTest {

  private static final String right = "Right";

  private HashSet<StepAction> actions = new HashSet<>();

  private CloudManagerBuildAction data;

  @Injectable
  private StepContext context;

  @Mocked
  private TaskListener taskListener;

  @Mocked
  private PipelineExecutionStepState stepState;

  @Mocked
  private PipelineExecution pipelineExecution;

  @Before
  public void before() {
    new MockUp<AbstractStepExecution>() {
      @Mock
      protected CloudManagerBuildAction getBuildData() {
        return data;
      }
    };
    data = new CloudManagerBuildAction(AIO_PROJECT_NAME, new CloudManagerPipelineExecution(right, right, right));
    actions.add(StepAction.build);
    actions.add(StepAction.deploy);
    actions.add(StepAction.codeQuality);
  }

  @Test
  public void handlesInvalidAction() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true);

    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions,false, true);
    new Expectations() {{
      stepState.getAction();
      result = "Unknown";
      context.get(TaskListener.class);
      result = taskListener;
      taskListener.getLogger();
      result = ps;
    }};

    assertFalse(tested.isApplicable(stepState));
    ps.close();
    assertEquals(baos.toString(), Messages.PipelineStepStateExecution_unknownStepAction("Unknown") + '\n');
  }

  @Test
  public void doesNotWantWrongAction() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      stepState.getAction();
      result = StepAction.reportPerformanceTest.name();
    }};

    assertFalse(tested.isApplicable(stepState));
  }

  @Test
  public void wantsStep() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      stepState.getAction();
      result = StepAction.build.name();
    }};

    assertTrue(tested.isApplicable(stepState));
  }

  @Test
  public void doesNotWantWrongProgram() throws Exception {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = "Wrong";
    }};

    assertFalse(tested.isApplicable(pipelineExecution));
  }

  @Test
  public void doesNotWantWrongPipeline() throws Exception {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = "Wrong";
    }};

    assertFalse(tested.isApplicable(pipelineExecution));
  }

  @Test
  public void doesNotWantWrongExecution() throws Exception {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = right;
      pipelineExecution.getId();
      result = "Wrong";
    }};

    assertFalse(tested.isApplicable(pipelineExecution));
  }

  @Test
  public void wantsExecution() throws Exception {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions, false, true);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = right;
      pipelineExecution.getId();
      result = right;
    }};

    assertTrue(tested.isApplicable(pipelineExecution));
  }
}

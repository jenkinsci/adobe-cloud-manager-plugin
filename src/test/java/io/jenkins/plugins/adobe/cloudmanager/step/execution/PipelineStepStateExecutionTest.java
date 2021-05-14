package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.util.HashSet;

import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.util.CloudManagerBuildData;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PipelineStepStateExecutionTest {

  private static final String wrong = "Wrong";
  private static final String right = "Right";

  private HashSet<StepAction> actions = new HashSet<>();

  private CloudManagerBuildData data = new CloudManagerBuildData();

  @Injectable
  private StepContext context;

  @Mocked
  private PipelineExecutionStepState stepState;

  @Mocked
  private PipelineExecution pipelineExecution;

  @Before
  public void before() {
    new MockUp<AbstractStepExecution>() {
      @Mock
      protected CloudManagerBuildData getBuildData() {
        return data;
      }
    };

    data.setProgramId(right);
    data.setPipelineId(right);
    data.setExecutionId(right);

    actions.add(StepAction.build);
    actions.add(StepAction.deploy);
    actions.add(StepAction.codeQuality);
  }

  @Test
  public void handlesInvalidAction() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      stepState.getAction();
      result = "Unknown";
    }};

    assertFalse(tested.wantsStep().apply(stepState));
  }

  @Test
  public void doesNotWantWrongAction() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      stepState.getAction();
      result = StepAction.reportPerformanceTest.name();
    }};

    assertFalse(tested.wantsStep().apply(stepState));
  }

  @Test
  public void wantsStep() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      stepState.getAction();
      result = StepAction.build.name();
    }};

    assertTrue(tested.wantsStep().apply(stepState));
  }

  @Test
  public void doesNotWantWrongProgram() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = "Wrong";
    }};

    assertFalse(tested.wantsExecution().apply(pipelineExecution));
  }

  @Test
  public void doesNotWantWrongPipeline() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = "Wrong";
    }};

    assertFalse(tested.wantsExecution().apply(pipelineExecution));
  }

  @Test
  public void doesNotWantWrongExecution() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = right;
      pipelineExecution.getId();
      result = "Wrong";
    }};

    assertFalse(tested.wantsExecution().apply(pipelineExecution));
  }

  @Test
  public void wantsExecution() {
    PipelineStepStateExecution tested = new PipelineStepStateExecution(context, actions);
    new Expectations() {{
      pipelineExecution.getProgramId();
      result = right;
      pipelineExecution.getPipelineId();
      result = right;
      pipelineExecution.getId();
      result = right;
    }};

    assertTrue(tested.wantsExecution().apply(pipelineExecution));
  }
}

package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.step.PipelineStepStateAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineStepStateExecution extends AbstractStepExecution {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepStateExecution.class);

  private final Set<StepAction> actions;
  private final String id;

  public PipelineStepStateExecution(StepContext context, Set<StepAction> actions) {
    super(context);
    this.actions = actions;
    id = UUID.randomUUID().toString();
  }

  public String getId() {
    return id;
  }

  public Function<PipelineExecutionStepState, Boolean> wantsStep() {
    return (state) -> {
      try {
        StepAction action = StepAction.valueOf(state.getAction());
        return actions.contains(action);
      } catch (IllegalArgumentException e) {
        TaskListener listener;
        try {
          listener = getContext().get(TaskListener.class);
          if (listener != null) {
            listener.getLogger().println(Messages.PipelineStepStateExecution_error_unknownStepAction(state.getAction()));
          }
        } catch (IOException | InterruptedException ex) {
          // Nothing we can do, can't even log it.
        }
        return false;
      }
    };
  }

  public Function<PipelineExecution, Boolean> wantsExecution() {
    return (pe) -> StringUtils.equals(getBuildData().getProgramId(), pe.getProgramId()) &&
        StringUtils.equals(getBuildData().getPipelineId(), pe.getPipelineId()) &&
        StringUtils.equals(getBuildData().getExecutionId(), pe.getId());
  }

  public void occurred(PipelineExecution pe, PipelineExecutionStepState state) throws IOException, InterruptedException {
    TaskListener listener = Objects.requireNonNull(getContext().get(TaskListener.class));
    listener.getLogger().println(Messages.PipelineStepStateExecution_event_occurred(pe.getId(), state.getAction(), state.getStatusState()));
    doFinish();
    getContext().onSuccess(null);
  }

  @Override
  public boolean doStart() throws Exception {
    // Pause the flow.
    getAction().add(this);
    FlowNode node = Objects.requireNonNull(getContext().get(FlowNode.class));
    node.addAction(new PauseAction("Pipeline Execution Step State"));
    TaskListener listener = Objects.requireNonNull(getContext().get(TaskListener.class));
    listener.getLogger().println(Messages.PipelineStepStateExecution_info_waiting());
    return false;
  }

  @Override
  public void stop(@Nonnull Throwable cause) throws Exception {
    doFinish();
    super.stop(cause);
  }

  private PipelineStepStateAction getAction() throws IOException, InterruptedException {
    Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
    PipelineStepStateAction action = run.getAction(PipelineStepStateAction.class);
    if (action == null) {
      action = new PipelineStepStateAction();
      run.addAction(action);
    }
    return action;
  }

  private void doFinish() {
    try {
      Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
      getAction().remove(this);
      run.save();
    } catch (IOException | InterruptedException | TimeoutException e) {
      LOGGER.warn(Messages.PipelineStepStateExecution_warn_actionRemoval());
    } finally {
      try {
        FlowNode node = getContext().get(FlowNode.class);
        if (node != null) {
          PauseAction.endCurrentPause(node);
        }
      } catch (IOException | InterruptedException e) {
        LOGGER.warn(Messages.PipelineStepStateExecution_warn_endPause());
      }
    }
  }
}

package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.model.TaskListener;
import io.adobe.cloudmanager.PipelineExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineEndExecution extends AbstractStepExecution {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEndExecution.class);

  private final Boolean mirror;
  boolean finished = false;

  public PipelineEndExecution(StepContext context, boolean mirror) {
    super(context);
    this.mirror = mirror;
  }

  public boolean isFinished() {
    return finished;
  }

  @Override
  public boolean doStart() throws Exception {
    if (getContext().hasBody()) {
      getContext().newBodyInvoker().withCallback(new Callback(getId())).start();
    } else {
      getTaskListener().getLogger().println(Messages.PipelineEndExecution_info_waiting());
    }
    return false;
  }

  @Override
  public void onResume() {
    super.onResume();
    getContext().newBodyInvoker().withCallback(new Callback(getId())).start();
  }

  @CheckForNull
  @Override
  public String getStatus() {
    if (!finished) {
      return "waiting for event";
    } else {
      return "apparently finished, maybe cleaning up?";
    }
  }

  /**
   * Called when this hasn't received the remote event, to repeat any body logic.
   */
  public void rerun(StepContext bodyContext) {
    bodyContext.newBodyInvoker().withCallback(new Callback(getId())).start();
  }

  /**
   * We're done working and the body has finished its work.
   */
  public void end() {
    getContext().onSuccess(null);
  }

  public Function<PipelineExecution, Boolean> wants() {
    return (pe) -> StringUtils.equals(getBuildData().getProgramId(), pe.getProgramId()) &&
        StringUtils.equals(getBuildData().getPipelineId(), pe.getPipelineId()) &&
        StringUtils.equals(getBuildData().getExecutionId(), pe.getId());
  }

  // Event handling

  /**
   * Processes the execution event, will quietly terminate any internal {@link PipelineStepStateExecution}s.
   *
   * If any unknown body steps are waiting, they'll block this event processing from early termination of this step.
   */
  public void occurred(@Nonnull PipelineExecution pe) throws IOException, InterruptedException {
    finished = true;
    TaskListener listener = getTaskListener();
    try {
      StepExecution.applyAll(PipelineStepStateExecution.class, (execution) -> {
        try {
          execution.doEndQuietly();
        } catch (IOException | InterruptedException e) {
          getContext().onFailure(e);
        }
        return null;
      }).get();
    } catch (ExecutionException e) {
      getContext().onFailure(e);
    }
    listener.getLogger().println(Messages.PipelineEndExecution_event_occurred(pe.getId(), pe.getStatusState()));
  }

  /**
   * Callback for handling end of body block
   */
  private static final class Callback extends BodyExecutionCallback {
    private static final long serialVersionUID = 1;
    private final String id;

    Callback(String id) {
      this.id = id;
    }
    @Override
    public void onSuccess(StepContext context, Object result) {
      StepExecution.applyAll(PipelineEndExecution.class, (execution) -> {
        if (execution.getId().equals(id)) {
          if (execution.isFinished()) {
            execution.end();
          } else {
            execution.rerun(context);
          }
        }
        return null;
      });
    }

    @Override
    public void onFailure(StepContext context, Throwable t) {
      context.onFailure(t);
    }
  }
}

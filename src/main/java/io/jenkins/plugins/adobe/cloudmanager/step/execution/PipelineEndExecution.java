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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Result;
import hudson.model.TaskListener;
import io.adobe.cloudmanager.PipelineExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutionIterator;
import static io.adobe.cloudmanager.PipelineExecution.Status.*;

/**
 * Execution for a {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineEndStep}. Handles the any associated events.
 */
public class PipelineEndExecution extends AbstractStepExecution {

  private static final long serialVersionUID = 1L;

  private final boolean mirror;
  private final boolean empty;
  // Event status' which are associated with a remote pipeline failure.
  private final List<PipelineExecution.Status> FAILURES = Arrays.asList(FAILED, ERROR, CANCELLED);
  // Final status indicating that this execution is complete.
  private PipelineExecution.Status status;

  public PipelineEndExecution(StepContext context, boolean mirror, boolean empty) {
    super(context);
    this.mirror = mirror;
    this.empty = empty;
  }

  public boolean isFinished() {
    return status != null;
  }

  @Override
  public void doStart() throws Exception {
    getTaskListener().getLogger().println(Messages.PipelineEndExecution_waiting());
    if (!empty) {
      getContext().newBodyInvoker().withCallback(new Callback(getId())).start();
    }
  }

  @Override
  public void doResume() {
    try {
      getTaskListener().getLogger().println(Messages.PipelineEndExecution_waiting());
    } catch (IOException | InterruptedException e) {
      getContext().onFailure(e);
    }
  }

  @CheckForNull
  @Override
  public String getStatus() {
    if (!isFinished()) {
      return "waiting for event";
    } else {
      return "apparently finished, maybe cleaning up?";
    }
  }

  /**
   * Reruns the body, as this step hasn't receiving an end event as yet.
   */
  public void rerun(StepContext bodyContext) {
    bodyContext.newBodyInvoker().withCallback(new Callback(getId())).start();
  }

  /**
   * Ends this step when receiving an associated event.
   */
  public void end() {
    if (mirror && FAILURES.contains(status)) {
      getContext().onFailure(new FlowInterruptedException(Result.FAILURE, new RemoteStateInterruption(status)));
    } else {
      getContext().onSuccess(null);
    }
  }

  /**
   * indicates if this executions is associated with the remote Cloud Manager pipeline.
   */
  public boolean isApplicable(PipelineExecution pe) throws IOException, InterruptedException {
    return getBuildData().getCmExecution().equalTo(pe);
  }

  // Event handling

  /**
   * Processes the execution event, will quietly terminate any internal running {@link PipelineStepStateExecution} instances.
   * <p>
   * If any unknown body steps are waiting, they'll block this event processing from early termination of this step.
   * </p>
   */
  public void occurred(@Nonnull PipelineExecution pe) throws IOException, InterruptedException {
    status = pe.getStatusState();
    getContext().saveState();
    StepExecutionIterator.all().stream().map((sei) -> {
      sei.apply((se) -> {
        try {
          if (se instanceof PipelineStepStateExecution && ((PipelineStepStateExecution) se).getRun() == getRun()) {
            ((PipelineStepStateExecution) se).doEndQuietly();
          }
        } catch (IOException | InterruptedException e) {
          getContext().onFailure(e);
        }
        return null;
      });
      return sei;
    }).collect(Collectors.toList());
    getTaskListener().getLogger().println(Messages.PipelineEndExecution_occurred(pe.getId(), pe.getStatusState()));
    if (empty) {
      end();
    }
  }

  /**
   * Callback for handling end of body block.
   */
  private static final class Callback extends BodyExecutionCallback {
    private static final long serialVersionUID = 1;
    private final String id;

    Callback(String id) {
      this.id = id;
    }

    /**
     * Called when the body completes. If this execution isn't complete, will rerun the body.
     */
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

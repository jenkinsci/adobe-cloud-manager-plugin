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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import hudson.AbortException;
import hudson.console.HyperlinkNote;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Failure;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineStepDecisionAction;
import io.jenkins.plugins.adobe.cloudmanager.action.PipelineWaitingAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.PipelineExecutionStepState.Status.*;

/**
 * Execution for a {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineStepStateStep}. Handles the any associated events.
 */
public class PipelineStepStateExecution extends AbstractStepExecution {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepStateExecution.class);
  // We can only handle a few of the waiting actions. If more come up, add them here.
  private static final Set<StepAction> WAITING_ACTIONS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(StepAction.codeQuality, StepAction.approval)));

  private static final Set<PipelineExecutionStepState.Status> ENDED_STATUS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ERROR, FINISHED, FAILED, ROLLED_BACK, CANCELLED)));

  private final Set<StepAction> actions;
  private final boolean autoApprove;
  private final boolean advance;

  // Used as the reason for a waiting action. If its set - then we're waiting for user input.
  private StepAction reason;

  public PipelineStepStateExecution(StepContext context, Set<StepAction> actions, boolean autoApprove, boolean advance) {
    super(context);
    this.actions = actions;
    this.autoApprove = autoApprove;
    this.advance = advance;
  }

  @CheckForNull
  public StepAction getReason() {
    return reason;
  }

  // Execution Logic
  @Override
  public void doStart() throws Exception {
    getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_waiting());
  }

  @Override
  public void doResume() throws IOException, InterruptedException {
    if (reason == null) {
      getTaskListener().getLogger().println(Messages._PipelineStepStateExecution_waiting());
    }
  }

  @Override
  public void doStop() throws Exception {
    doFinish();
  }

  // Methods for filtering incoming events

  /**
   * Determines if this execution will process the associated remote Step State.
   */
  public boolean isApplicable(PipelineExecutionStepState stepState) {
    try {
      StepAction action = StepAction.valueOf(stepState.getAction());
      return actions.contains(action);
    } catch (IllegalArgumentException e) {
      try {
        getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_unknownStepAction(stepState.getAction()));
      } catch (IOException | InterruptedException ex) {
        // Nothing we can do, can't even log it.
      }
      return false;
    }
  }

  /**
   * Determines if this execution will process the remote Pipeline Execution.
   */
  public boolean isApplicable(PipelineExecution pe) throws IOException, InterruptedException {
    return getBuildData().getCmExecution().equalTo(pe);
  }

  // Event handling

  /**
   * Process an <i>occurred</i> event. Essentially, an event that does not require user input, but that should generate some informational message.
   */
  public void occurred(@Nonnull PipelineExecution pe, @Nonnull PipelineExecutionStepState state) throws IOException, InterruptedException {
    try {
      PipelineExecutionStepState.Status status = logStepAction(pe, state);
      doFinish();
      if (advance && ENDED_STATUS.contains(status)) {
        if (status == FINISHED || status == ROLLED_BACK) {
          getContext().onSuccess(null);
        } else if (status == CANCELLED) {
          FlowInterruptedException e = new FlowInterruptedException(Result.ABORTED, new Cancellation());
          getContext().onFailure(e);
        } else {
          FlowInterruptedException e = new FlowInterruptedException(Result.FAILURE, new io.jenkins.plugins.adobe.cloudmanager.step.execution.Failure());
          getContext().onFailure(e);
        }
      }
    } catch (IllegalArgumentException e) {
      getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_unknownStepAction(state.getAction()));
    }
  }

  /**
   * Process an <i>waiting</i> event. Waiting events pause this step/pipeline/run until a user action is taken.
   */
  public void waiting(@Nonnull PipelineExecution pe, @Nonnull PipelineExecutionStepState state) throws IOException, InterruptedException, TimeoutException {
    try {
      reason = StepAction.valueOf(state.getAction());
      logStepAction(pe, state);
      if (WAITING_ACTIONS.contains(reason)) {
        if (autoApprove) {
          approveStep();
          getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_autoApprove());
          doFinish();
        } else {
          startWaiting();
        }
      } else {
        getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_unknownWaitingAction(reason));
      }
    } catch (IllegalArgumentException e) {
      getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_unknownStepAction(state.getAction()));
    } catch (CloudManagerApiException e) {
      getContext().onFailure(e);
    }
  }

  private PipelineExecutionStepState.Status logStepAction(@Nonnull PipelineExecution pe, @Nonnull PipelineExecutionStepState state) throws IOException, InterruptedException {
    StepAction action = StepAction.valueOf(state.getAction());
    PipelineExecutionStepState.Status status = state.getStatusState();
    getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_occurred(pe.getId(), action, status));
    getBuildData().addStep(new CloudManagerBuildAction.PipelineStep(action, status, isHasLogs(state, status)));
    return status;
  }

  private boolean isHasLogs(@Nonnull PipelineExecutionStepState state, PipelineExecutionStepState.Status status) {
    if (!state.hasLogs()) {
        return false;
    } else {
      if (ENDED_STATUS.contains(status)) {
        return true;
      } else {
        return status == WAITING && StepAction.codeQuality == StepAction.valueOf(state.getAction());
      }
    }
  }

  /**
   * Flag to indicate that this step is waiting for user interaction.
   */
  public boolean isProcessed() {
    return reason == null;
  }

  // User/UI/REST Interaction

  /**
   * Form Submission
   */
  @RequirePOST
  public HttpResponse doSubmit(StaplerRequest request) throws IOException, ServletException, InterruptedException {
    if (request.getParameter("proceed") != null) {
      return doProceed();
    }
    return doCancel();
  }

  /**
   * REST Submission
   */
  @RequirePOST
  public HttpResponse doProceed() throws IOException, ServletException, InterruptedException {
    return proceed();
  }

  /**
   * REST Submission
   */
  @RequirePOST
  public HttpResponse doCancel() throws IOException, InterruptedException {
    try {
      preCancelCheck();
      CloudManagerPipelineExecution cmExecution = getRun().getAction(CloudManagerBuildAction.class).getCmExecution();
      getApi().cancelExecution(cmExecution.getProgramId(), cmExecution.getPipelineId(), cmExecution.getExecutionId());
      doFinish();
    } catch (CloudManagerApiException e) {
      doFinish();
      getContext().onFailure(e);
    }
    return HttpResponses.redirectTo("../..");
  }

  /**
   * Used by wrapping block steps to quietly end this step without changing the build status.
   * <p>
   * Primarily intended to be used by {@link io.jenkins.plugins.adobe.cloudmanager.step.PipelineEndStep}.
   * </p>
   */
  @Restricted(NoExternalUse.class)
  public void doEndQuietly() throws IOException, InterruptedException {
    // This may be blocking VM threads....
    getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_endQuietly());
    doFinish();
    getContext().onSuccess(null);
  }

  // Process the request to complete the wait event as "successful."
  private HttpResponse proceed() throws IOException, InterruptedException {
    String userId = User.current() != null ? User.current().getId() : "anonymous";
    Run<?, ?> run = getRun();
    TaskListener listener = getTaskListener();

    try {
      preApproveCheck();
      approveStep();
      run.addAction(new PipelineStepDecisionAction(userId, reason, PipelineStepDecisionAction.Decision.APPROVED));
      listener.getLogger().println(Messages.PipelineStepStateExecution_approvedBy(userId));
      doFinish();
    } catch (AbortException | CloudManagerApiException e) {
      doFinish();
      getContext().onFailure(e);
    }
    return HttpResponses.redirectTo("../..");
  }

  // Protection & Validation

  // Make sure the user executing the approval has the right authentication.
  private void preApproveCheck() throws IOException, InterruptedException {
    if (isProcessed()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_processed());
    }
    if (!canApprove()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_buildPermission());
    }
  }

  // Make sure the user executing the cancel has the right authentication.
  private void preCancelCheck() throws IOException, InterruptedException {
    if (isProcessed()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_processed());
    }
    if (!canAbort() && !canApprove()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_cancelPermission());
    }
  }

  private boolean canApprove() throws IOException, InterruptedException {
    return getRun().getParent().hasPermission(Job.BUILD);
  }

  private boolean canAbort() throws IOException, InterruptedException {
    return getRun().getParent().hasPermission(Job.CANCEL);
  }

  // Create the Run action to handle storing the execution which will process the Approval/Rejection
  private PipelineWaitingAction getAction() throws IOException, InterruptedException {
    Run<?, ?> run = getRun();
    PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
    if (action == null) {
      action = new PipelineWaitingAction();
      run.addAction(action);
    }
    return action;
  }

  // Wait for the user input
  private void startWaiting() throws IOException, InterruptedException, TimeoutException {
    getContext().saveState();
    getAction().add(this);
    String url = String.format("/%s%s/", getRun().getUrl(), getAction().getUrlName());
    getTaskListener().getLogger().println(HyperlinkNote.encodeTo(url, Messages.PipelineStepStateExecution_waitingApproval()));
    FlowNode node = Objects.requireNonNull(getContext().get(FlowNode.class));
    node.addAction(new PauseAction("Pipeline Execution Step State"));
  }

  // Advance the step.
  private void approveStep() throws IOException, InterruptedException, CloudManagerApiException {
    CloudManagerPipelineExecution cmExecution = getRun().getAction(CloudManagerBuildAction.class).getCmExecution();
    getApi().advanceExecution(cmExecution.getProgramId(), cmExecution.getPipelineId(), cmExecution.getExecutionId());
  }

  // Clean up this when done. Regardless of result.
  private void doFinish() {
    if (reason != null) {
      try {
        getAction().remove(this);
        getRun().removeAction(getAction());
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
      reason = null;
    }
  }
}

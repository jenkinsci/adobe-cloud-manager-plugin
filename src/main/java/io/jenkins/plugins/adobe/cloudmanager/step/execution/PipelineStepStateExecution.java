package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;

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

public class PipelineStepStateExecution extends AbstractStepExecution {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepStateExecution.class);
  // We can only handle a few of the waiting actions. If more come up, add them here.
  private static final Set<StepAction> WAITING_ACTIONS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(StepAction.codeQuality, StepAction.approval)));

  private final Set<StepAction> actions;
  private StepAction reason;

  public PipelineStepStateExecution(StepContext context, Set<StepAction> actions) {
    super(context);
    this.actions = actions;
  }

  @CheckForNull
  public StepAction getReason() {
    return reason;
  }

  // Execution Logic
  @Override
  public boolean doStart() throws Exception {
    // Pause the flow.
    getAction().add(this);
    getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_info_waiting());
    return false;
  }

  @Override
  public void onResume() {
    try {
      startWaiting();
    } catch (IOException | InterruptedException e) {
      getContext().onFailure(e);
    }
  }

  @Override
  public void stop(@Nonnull Throwable cause) throws Exception {
    doFinish();
    getContext().onFailure(cause);
  }

  // Filters for looking up waiting steps.
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

  // Event handling
  public void occurred(@Nonnull PipelineExecution pe, @Nonnull PipelineExecutionStepState state) throws IOException, InterruptedException {
    TaskListener listener = getTaskListener();
    listener.getLogger().println(Messages.PipelineStepStateExecution_event_occurred(pe.getId(), state.getAction(), state.getStatusState()));
    doFinish();
    getContext().onSuccess(null);
  }

  public void waiting(@Nonnull PipelineExecution pe, @Nonnull PipelineExecutionStepState state) throws IOException, InterruptedException, TimeoutException {
    TaskListener listener = getTaskListener();
    Run<?, ?> run = getRun();
    String action = state.getAction();
    listener.getLogger().println(Messages.PipelineStepStateExecution_event_occurred(pe.getId(), action, state.getStatusState()));
    try {
      reason = StepAction.valueOf(action);
      if (WAITING_ACTIONS.contains(reason)) {
        startWaiting();
      } else {
        listener.getLogger().println(Messages.PipelineStepStateExecution_error_unknownWaitingAction(action));
        doFinish();
        getContext().onFailure(new IllegalArgumentException(Messages.PipelineStepStateExecution_error_unknownWaitingAction(action)));
      }
    } catch (IllegalArgumentException e) {
      listener.getLogger().println(Messages.PipelineStepStateExecution_error_unknownStepAction(action));
      doFinish();
      getContext().onFailure(e);
    }
  }

  // Wait for the user input
  private void startWaiting() throws IOException, InterruptedException {
    String url = String.format("/%s%s/", getRun().getUrl(), getAction().getUrlName());
    getTaskListener().getLogger().println(HyperlinkNote.encodeTo(url, Messages.PipelineStepStateExecution_prompt_waitingApproval()));
    FlowNode node = Objects.requireNonNull(getContext().get(FlowNode.class));
    node.addAction(new PauseAction("Pipeline Execution Step State"));
    getContext().saveState();
  }

  /**
   * If we're waiting, then reason will be populated. It gets removed once something happens.
   *
   * @return true if this step is finished waxriting
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

  @RequirePOST
  public HttpResponse doCancel() throws IOException, InterruptedException {
    try {
      preCancelCheck();
      reason = null;
      CloudManagerBuildAction buildData = getRun().getAction(CloudManagerBuildAction.class);
      getApi().cancelExecution(buildData.getProgramId(), buildData.getPipelineId(), buildData.getExecutionId());
      FlowInterruptedException e = new FlowInterruptedException(Result.ABORTED, new Cancellation(User.current()));
      doFinish();
      getContext().onFailure(e);
    } catch (CloudManagerApiException e) {
      doFinish();
      getContext().onFailure(e);
    }
    return HttpResponses.redirectTo("../..");
  }

  @Restricted(NoExternalUse.class)
  public void doEndQuietly() throws IOException, InterruptedException {
    reason = null;
    getTaskListener().getLogger().println(Messages.PipelineStepStateExecution_info_endQuietly());
    doFinish();
    getContext().onSuccess(null);
  }

  private HttpResponse proceed() throws IOException, InterruptedException {
    User user = User.current();
    Run<?, ?> run = getRun();
    TaskListener listener = getTaskListener();
    if (user != null) {
      run.addAction(new PipelineStepDecisionAction(user.getId(), reason, PipelineStepDecisionAction.Decision.APPROVED));
      listener.getLogger().println(Messages.PipelineStepStateExecution_info_approvedBy(ModelHyperlinkNote.encodeTo(user)));
    }

    try {
      preApproveCheck();
      reason = null; // Null the reason now, not after API - API call may take a bit
      CloudManagerBuildAction buildData = getRun().getAction(CloudManagerBuildAction.class);
      getApi().advanceExecution(buildData.getProgramId(), buildData.getPipelineId(), buildData.getExecutionId());
      doFinish();
      getContext().onSuccess(null);
    } catch (AbortException | CloudManagerApiException e) {
      doFinish();
      getContext().onFailure(e);
    }
    return HttpResponses.redirectTo("../..");
  }

  // Protection & Validation
  private void preApproveCheck() throws IOException, InterruptedException {
    if (isProcessed()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_processed());
    }
    if (!canApprove()) {
      throw new Failure(Messages.PipelineStepStateExecution_failure_buildPermission());
    }
  }

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

  // Make sure Action exists
  private PipelineWaitingAction getAction() throws IOException, InterruptedException {
    Run<?, ?> run = getRun();
    PipelineWaitingAction action = run.getAction(PipelineWaitingAction.class);
    if (action == null) {
      action = new PipelineWaitingAction();
      run.addAction(action);
    }
    return action;
  }

  // Clean up this when done. Regardless of result.
  private void doFinish() {
    try {
      Run<?, ?> run = getRun();
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

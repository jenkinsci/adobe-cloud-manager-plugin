package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import java.util.List;

import hudson.AbortException;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import io.jenkins.plugins.adobe.cloudmanager.CloudManagerPipelineExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Execution for a {@link io.jenkins.plugins.adobe.cloudmanager.step.AdvancePipelineStep}, advancing the remote Cloud Manager pipeline.
 */
public class AdvancePipelineExecution extends AbstractStepExecution {

  private final List<StepAction> actions;

  public AdvancePipelineExecution(StepContext context, List<StepAction> actions) {
    super(context);
    this.actions = actions;
  }

  @Override
  public void doStart() throws Exception {
    try {
      CloudManagerPipelineExecution build = getBuildData().getCmExecution();
      CloudManagerApi api = getApi();
      PipelineExecution pe = api.getExecution(build.getProgramId(), build.getPipelineId(), build.getExecutionId());

      PipelineExecutionStepState step = api.getCurrentStep(pe);
      StepAction stepAction = StepAction.valueOf(step.getAction());
      if (actions.contains(stepAction)) {
        getTaskListener().getLogger().println(Messages.AdvancePipelineExecution_info_advancingPipeline(stepAction));
        api.advanceExecution(pe);
        getContext().onSuccess(null);
      } else {
        throw new AbortException(Messages.AdvancePipelineExecution_error_invalidPipelineState(stepAction));
      }
    } catch (CloudManagerApiException e) {
      throw new AbortException(e.getLocalizedMessage());
    }
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}

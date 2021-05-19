package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import io.adobe.cloudmanager.PipelineExecution;
import jenkins.model.CauseOfInterruption;


/**
 * An interruption which occurs due to remote selection, cancellation or otherwise.
 */
public class RemoteStateInterruption extends CauseOfInterruption {

  private final PipelineExecution.Status reason;

  public RemoteStateInterruption(PipelineExecution.Status reason) {
    this.reason = reason;
  }

  @Override
  public String getShortDescription() {
    return Messages.RemoteStateInterruption_failure_remoteError(reason);
  }
}

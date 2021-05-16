package io.jenkins.plugins.adobe.cloudmanager.action;

import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;

/**
 * Pipeline End Execution specific Action
 */
public class PipelineEndAction extends PipelineAction<PipelineEndExecution> {
  @Override
  public Class<PipelineEndExecution> getType() {
    return PipelineEndExecution.class;
  }
}

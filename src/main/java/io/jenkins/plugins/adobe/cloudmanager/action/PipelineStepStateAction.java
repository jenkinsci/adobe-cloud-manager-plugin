package io.jenkins.plugins.adobe.cloudmanager.action;

import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;

/**
 * Pipeline Step State Execution specific Action
 */
public class PipelineStepStateAction extends PipelineAction<PipelineStepStateExecution> {
  @Override
  public Class<PipelineStepStateExecution> getType() {
    return PipelineStepStateExecution.class;
  }
}

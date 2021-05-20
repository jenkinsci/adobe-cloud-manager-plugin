package io.jenkins.plugins.adobe.cloudmanager.action;

import java.io.Serializable;

import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.StepAction;
import lombok.Value;

/**
 * Represents a step which a Cloud Manager Pipeline execution reached.
 *
 * Used in {@link CloudManagerBuildAction} for tracking state and linking logs.
 */
@Value
public class PipelineStep implements Serializable {

  private static final long serialVersionUID = 1L;

  StepAction action;
  PipelineExecutionStepState.Status status;
  boolean hasLogs;
}

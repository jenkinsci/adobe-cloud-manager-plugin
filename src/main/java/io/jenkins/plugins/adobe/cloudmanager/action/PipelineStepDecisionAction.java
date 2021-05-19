package io.jenkins.plugins.adobe.cloudmanager.action;

import io.adobe.cloudmanager.StepAction;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;

/**
 * Action for storing the decision made by a user on a Cloud Manager build step.
 */
public class PipelineStepDecisionAction implements PersistentAction {

  private final String userId;
  private final StepAction step;
  private final Decision decision;

  public PipelineStepDecisionAction(String userId, StepAction step, Decision decision) {
    this.userId = userId;
    this.step = step;
    this.decision = decision;
  }

  @Override
  public String getDisplayName() {
    return Messages.PipelineStepDecisionAction_displayName(userId, decision, step);
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return null;  // Returning null keeps it off the left nav.
  }

  /**
   * Options for a decision.
   */
  public enum Decision {
    APPROVED, REJECTED
  }
}

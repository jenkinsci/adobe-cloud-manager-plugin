package io.jenkins.plugins.adobe.cloudmanager.action;

import io.adobe.cloudmanager.StepAction;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;

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
    return null;
  }

  public enum Decision {
    APPROVED, REJECTED
  }
}

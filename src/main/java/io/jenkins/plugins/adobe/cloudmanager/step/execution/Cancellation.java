package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import javax.annotation.CheckForNull;

import hudson.model.User;
import jenkins.model.CauseOfInterruption;

public class Cancellation extends CauseOfInterruption {
  private static final long serialVersionUID = 1;

  @CheckForNull
  private final String userName;

  public Cancellation(@CheckForNull User user) {
    this.userName = user != null ? user.getId() : null;
  }

  @Override
  public String getShortDescription() {

    if (userName != null) {
      return Messages.Cancellation_description_by(userName);
    } else {
      return Messages.Cancellation_description();
    }
  }
}

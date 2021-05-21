package io.jenkins.plugins.adobe.cloudmanager.trigger;

import hudson.model.Cause;
import lombok.Value;

@Value
public class CMPipelineStartCause extends Cause {

  String eventId;

  @Override
  public String getShortDescription() {
    return Messages.CMPipelineStartCause_shortDescription(eventId);
  }
}

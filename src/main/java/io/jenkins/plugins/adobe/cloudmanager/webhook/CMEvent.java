package io.jenkins.plugins.adobe.cloudmanager.webhook;

import io.adobe.cloudmanager.event.CloudManagerEvent;
import lombok.Value;

@Value
public class CMEvent {
  CloudManagerEvent.EventType eventType;
  String imsOrg;
  String payload;
}

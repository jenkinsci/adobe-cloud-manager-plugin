package io.jenkins.plugins.adobe.cloudmanager.webhook;

import io.adobe.cloudmanager.event.CloudManagerEvent;
import lombok.Value;

/**
 * A Cloud Manager Event received from a WebHook call. Helper for validation essentially.
 */
@Value
public class CMEvent {
  CloudManagerEvent.EventType eventType;
  String imsOrg;
  String payload;
}

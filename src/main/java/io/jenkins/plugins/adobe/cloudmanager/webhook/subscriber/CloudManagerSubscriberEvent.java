package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

import javax.annotation.Nonnull;

import io.adobe.cloudmanager.event.CloudManagerEvent;
import lombok.Value;

@Value
public class CloudManagerSubscriberEvent {
  @Nonnull
  String aioProjectName;
  @Nonnull
  CloudManagerEvent.EventType type;
  @Nonnull
  String payload;
}

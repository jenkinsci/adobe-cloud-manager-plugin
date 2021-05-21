package io.jenkins.plugins.adobe.cloudmanager.trigger;

import java.time.OffsetDateTime;

import lombok.Value;

/**
 * Start event for notifying an Adobe Cloud Manager pipeline as started.
 */
@Value
public class PipelineStartEvent {
  String aioEventId;
  String aioProject;
  String programId;
  String pipelineId;
  String executionId;
  OffsetDateTime started;
}

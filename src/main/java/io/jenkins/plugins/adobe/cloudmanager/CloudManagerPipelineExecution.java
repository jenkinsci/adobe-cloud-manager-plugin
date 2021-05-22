package io.jenkins.plugins.adobe.cloudmanager;

import org.apache.commons.lang3.StringUtils;

import io.adobe.cloudmanager.PipelineExecution;
import lombok.Value;

/**
 * Representation of a Cloud Manager Pipeline Execution. Used to reduce repetition logic.
 */
@Value
public class CloudManagerPipelineExecution {
  String programId;
  String pipelineId;
  String executionId;

  public boolean equalTo(PipelineExecution pipelineExecution) {
    return StringUtils.equals(pipelineExecution.getProgramId(), programId) &&
        StringUtils.equals(pipelineExecution.getPipelineId(), pipelineId) &&
        StringUtils.equals(pipelineExecution.getId(), executionId);

  }
}

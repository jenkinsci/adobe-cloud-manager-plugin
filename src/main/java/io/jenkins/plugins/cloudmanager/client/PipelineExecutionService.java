package io.jenkins.plugins.cloudmanager.client;

import io.jenkins.plugins.cloudmanager.AdobeioConfig;
import io.swagger.client.api.PipelineExecutionApi;
import retrofit2.Call;

public class PipelineExecutionService extends AbstractService<PipelineExecutionApi> {

  public PipelineExecutionService(AdobeioConfig config) {
    super(config, PipelineExecutionApi.class);
  }

  public Call<Void> startPipeline(String programId, String pipelineId) {
    return api.startPipeline(
        programId, pipelineId, organizationId, authorization, apiKey, "application/json");
  }
}

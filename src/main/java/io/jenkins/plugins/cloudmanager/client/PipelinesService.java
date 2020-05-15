package io.jenkins.plugins.cloudmanager.client;

import io.jenkins.plugins.cloudmanager.AdobeioConfig;
import io.swagger.client.api.PipelinesApi;
import io.swagger.client.model.Pipeline;
import io.swagger.client.model.PipelineList;
import retrofit2.Call;

public class PipelinesService extends AbstractService<PipelinesApi> {

  public PipelinesService(AdobeioConfig config) {
    super(config, PipelinesApi.class);
  }

  public Call<Pipeline> getPipeline(String programId, String pipelineId) {
    return this.api.getPipeline(programId, pipelineId, organizationId, authorization, apiKey);
  }

  public Call<PipelineList> getPipelines(String programId) {
    return this.api.getPipelines(programId, organizationId, authorization, apiKey);
  }
}

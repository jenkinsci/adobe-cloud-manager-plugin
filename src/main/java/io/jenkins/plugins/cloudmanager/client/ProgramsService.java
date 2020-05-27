package io.jenkins.plugins.cloudmanager.client;

import io.jenkins.plugins.cloudmanager.AdobeioConfig;
import io.swagger.client.api.ProgramsApi;
import io.swagger.client.model.Program;
import io.swagger.client.model.ProgramList;
import retrofit2.Call;

public class ProgramsService extends AbstractService<ProgramsApi> {

  public ProgramsService(AdobeioConfig config) {
    super(config, ProgramsApi.class);
  }

  public Call<Program> getProgram(String programId) {
    return api.getProgram(programId, organizationId, authorization, getApiKey());
  }

  public Call<ProgramList> getPrograms() {
    return api.getPrograms(organizationId, authorization, getApiKey());
  }
}

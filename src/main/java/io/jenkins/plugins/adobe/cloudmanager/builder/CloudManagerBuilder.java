package io.jenkins.plugins.adobe.cloudmanager.builder;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import hudson.AbortException;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CloudManagerBuilder extends Builder implements SimpleBuildStep {
  protected static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerBuilder.class);

  protected String aioProject;
  protected String program;
  protected String pipeline;

  protected CloudManagerBuilder() {

  }

  public String getAioProject() {
    return aioProject;
  }

  @DataBoundSetter
  public void setAioProject(String aioProject) {
    this.aioProject = aioProject;
  }

  public String getProgram() {
    return program;
  }

  @DataBoundSetter
  public void setProgram(String program) {
    this.program = program;
  }

  public String getPipeline() {
    return pipeline;
  }

  @DataBoundSetter
  public void setPipeline(String pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Authenticate using this Builder's configured Adobe IO Project, and return an access token
   *
   * @param config the configuration to use for authentication
   * @return access token
   * @throws AbortException when any error occur during authentication
   */
  @Nonnull
  private Secret getAccessToken(@Nonnull AdobeIOProjectConfig config) throws AbortException {
    Secret token = config.authenticate();
    if (token == null) {
      throw new AbortException(Messages.CloudManagerBuilder_error_authenticate(
          Messages.CloudManagerBuilder_error_checkLogs()
      ));
    }
    return token;
  }

  /**
   * Create a {@link CloudManagerApi} from this Builder's configured Adobe IO project.
   *
   * @return a CloudManagerApi
   * @throws AbortException when any error occurs during API construction
   */
  @Nonnull
  public CloudManagerApi createApi() throws AbortException {
    AdobeIOProjectConfig config = AdobeIOConfig.projectConfigFor(aioProject);
    if (config == null) {
      throw new AbortException(Messages.CloudManagerBuilder_error_missingAioProject(aioProject));
    }
    return CloudManagerApi.create(config.getImsOrganizationId(), config.getClientId(), getAccessToken(config).getPlainText());
  }

  /**
   * Get the Program Id for this Builder's configured Program. Program can be specified as id or Name.
   *
   * @param api configured API for looking up the ProgramId.
   * @return the program id (as a String)
   * @throws AbortException when any error occurs finding the id
   */
  public String getProgramId(CloudManagerApi api) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(program));
    } catch (NumberFormatException e) {
      try {
        return api.listPrograms()
            .stream()
            .filter(p -> program.equals(p.getName()))
            .findFirst()
            .orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingProgram(program)))
            .getId();
      } catch (CloudManagerApiException ex) {
        throw new AbortException(Messages.CloudManagerBuilder_error_CloudManagerApiException(ex.getLocalizedMessage()));
      }
    }
  }

  /**
   * Get the Pipeline ID for this builder's configured Pipeline. Pipelines can be specified as an Id or Name.
   *
   * @param api configured API for looking up pipeline id
   * @param programId the program id context. Must be the <i>id</i>
   * @return the pipeline id (as a String)
   * @throws AbortException when any error occurs finding the id
   */
  public String getPipelineId(CloudManagerApi api, String programId) throws AbortException {
    try {
      return String.valueOf(Integer.parseInt(pipeline));
    } catch (NumberFormatException e) {
      try {
      return api.listPipelines(programId, new Pipeline.NamePredicate(pipeline))
          .stream()
          .findFirst()
          .orElseThrow(() -> new AbortException(Messages.CloudManagerBuilder_error_missingPipeline(pipeline)))
          .getId();
      } catch (CloudManagerApiException ex) {
        throw new AbortException(Messages.CloudManagerBuilder_error_CloudManagerApiException(ex.getLocalizedMessage()));
      }
    }
  }

  public static class CloudManagerBuilderDescriptor extends BuildStepDescriptor<Builder> {

    protected static CloudManagerApi createApi(String aioProject) {
      AdobeIOProjectConfig cfg = AdobeIOConfig.projectConfigFor(aioProject);

      if (cfg == null) {
        return null;
      }
      Secret token = cfg.authenticate();
      if (token == null) {
        return null;
      }
      return CloudManagerApi.create(cfg.getImsOrganizationId(), cfg.getClientId(), token.getPlainText());
    }

    public ListBoxModel doFillAioProjectItems() {
      ListBoxModel lbm = new ListBoxModel();
      lbm.add(Messages.CloudManagerBuilderDescriptor_defaultListItem(), "");
      AdobeIOConfig aio = AdobeIOConfig.configuration();
      for (AdobeIOProjectConfig cfg : aio.getProjectConfigs()) {
        lbm.add(cfg.getDisplayName(), cfg.getName());
      }
      return lbm;
    }

    public ListBoxModel doFillProgramItems(@QueryParameter String aioProject) {
      ListBoxModel lbm = new ListBoxModel();
      lbm.add(Messages.CloudManagerBuilderDescriptor_defaultListItem(), "");
      try {
        if (StringUtils.isNotBlank(aioProject)) {
          CloudManagerApi api = createApi(aioProject);
          List<Program> programs = api == null ? Collections.emptyList() : api.listPrograms();
          for (Program p : programs) {
            lbm.add(p.getName(), p.getId());
          }
        }
      } catch (CloudManagerApiException e) {
        LOGGER.error(Messages.CloudManagerBuilder_error_CloudManagerApiException(e.getLocalizedMessage()));
      }
      return lbm;
    }

    public ListBoxModel doFillPipelineItems(@QueryParameter String aioProject, @QueryParameter String program) {
      CloudManagerApi api = createApi(aioProject);
      ListBoxModel lbm = new ListBoxModel();
      lbm.add(Messages.CloudManagerBuilderDescriptor_defaultListItem(), "");

      try {
        if (StringUtils.isNotBlank(program)) {
          List<Pipeline> pipelines = api == null ? Collections.emptyList() : api.listPipelines(program);
          for (Pipeline p : pipelines) {
            lbm.add(p.getName(), p.getId());
          }
        }
      } catch (CloudManagerApiException e) {
        LOGGER.error(Messages.CloudManagerBuilder_error_CloudManagerApiException(e.getLocalizedMessage()));
      }
      return lbm;
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }
  }
}

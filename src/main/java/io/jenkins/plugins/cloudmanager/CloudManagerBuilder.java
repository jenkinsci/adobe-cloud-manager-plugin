package io.jenkins.plugins.cloudmanager;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.impl.CloudManagerApiImpl;
import io.adobe.cloudmanager.model.EmbeddedProgram;
import io.adobe.cloudmanager.model.Pipeline;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudManagerBuilder extends Builder implements SimpleBuildStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerBuilder.class);

  private String program;
  private String pipeline;

  @DataBoundConstructor
  public CloudManagerBuilder(String program, String pipeline) {
    this.program = program;
    this.pipeline = pipeline;
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

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) {
    PrintStream logger = listener.getLogger();
    CloudManagerGlobalConfig config = ExtensionList.lookupSingleton(CloudManagerGlobalConfig.class);

    Secret accessToken = config.retrieveAccessToken();

    if (accessToken == null) {
      throw new IllegalStateException(
          "Could not get an access token for the cloud manager API."
              + "Check the plugin configuration in Jenkins system config and "
              + "ensure the authentication values are correct");
    }

    if (StringUtils.isBlank(getProgram())) {
      throw new IllegalStateException("Program Value is not configured");
    } else if (StringUtils.isBlank(getPipeline())) {
      throw new IllegalStateException("Pipeline Value is not configured");
    }

    logger.println(
        "[INFO] Starting pipeline with programId: "
            + getProgram()
            + " and pipelineId: "
            + getPipeline());

    try {
      CloudManagerApi cmapi = new CloudManagerApiImpl(config.getOrganizationID(), config.getApiKey().getPlainText(), accessToken.getPlainText());
      cmapi.startExecution(getProgram(), getPipeline());
    } catch (CloudManagerApiException e) {
      throw new IllegalStateException("Pipeline was not started", e);
    }
    logger.println(
          "[SUCCESS] Pipeline was started successfully! You can monitor its progress in cloud manager.");
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Inject
    CloudManagerGlobalConfig config;

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Cloud Manager Build Step";
    }

    public ListBoxModel doFillProgramItems() throws IOException {
      Secret accessToken = config.retrieveAccessToken();
      ListBoxModel items = new ListBoxModel();
      items.add("Select Program", "");
      if (accessToken == null) {
        items.add("Could not get AdobeIO Access Token. Check Jenkins logs", "");
        return items;
      }

      CloudManagerApi cmapi = new CloudManagerApiImpl(config.getOrganizationID(), config.getApiKey().getPlainText(), accessToken.getPlainText());
      try {
        List<EmbeddedProgram> list = cmapi.listPrograms();
        list.forEach(p -> items.add(String.format("%s (%s)", p.getName(), p.getId()), p.getId()));
      } catch (CloudManagerApiException e) {
        items.add("Could not get programs. Check Jenkins logs", "");
        LOGGER.error("Request to get programs was not successful: {}", e.getMessage());
      }
      return items;
    }

    public ListBoxModel doFillPipelineItems(@QueryParameter String program) throws IOException {
      ListBoxModel items = new ListBoxModel();
      if (StringUtils.isBlank(program)) {
        return items;
      }

      Secret accessToken = config.retrieveAccessToken();
      if (accessToken == null) {
        items.add("Could not get AdobeIO Access Token. Check Jenkins logs", "");
        return items;
      }

      try {
        CloudManagerApi cmapi = new CloudManagerApiImpl(config.getOrganizationID(), config.getApiKey().getPlainText(), accessToken.getPlainText());
        List<Pipeline> pipelines = cmapi.listPipelines(program);
        pipelines.forEach(p -> items.add(String.format("%s (%s)", p.getName(), p.getId()), p.getId()));
      } catch (CloudManagerApiException e) {
        items.add("Could not get pipelines. Check Jenkins logs", "");
        LOGGER.error("Request to get pipelines was not successful. {}", e.getMessage());
      }
      return items;
    }
  }
}

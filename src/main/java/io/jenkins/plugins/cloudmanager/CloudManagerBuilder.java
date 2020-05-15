package io.jenkins.plugins.cloudmanager;

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
import io.jenkins.plugins.cloudmanager.client.PipelineExecutionService;
import io.jenkins.plugins.cloudmanager.client.PipelinesService;
import io.jenkins.plugins.cloudmanager.client.ProgramsService;
import java.io.IOException;
import java.io.PrintStream;
import javax.inject.Inject;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import retrofit2.Response;

public class CloudManagerBuilder extends Builder implements SimpleBuildStep {

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
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws InterruptedException, IOException {
    PrintStream logger = listener.getLogger();
    CloudManagerGlobalConfig config = ExtensionList.lookupSingleton(CloudManagerGlobalConfig.class);
    String accessToken = config.getAccessToken();

    if (StringUtils.isBlank(accessToken)) {
      throw new IllegalStateException(
          "Could not get an acess token for the cloud manager API."
              + "Check the plugin configuration in Jenkins system config and "
              + "ensure the authentication values are correct");
    }

    if (StringUtils.isBlank(getProgram())) {
      throw new IllegalStateException("Program Value is not configured");
    } else if (StringUtils.isBlank(getPipeline())) {
      throw new IllegalStateException("Pipeline Value is not configured");
    }

    logger.println("[INFO] Starting pipeline with programId: " +
        getProgram() +
        " and pipelineId: " +
        getPipeline());

    PipelineExecutionService executionService = new PipelineExecutionService(config);
    Response<Void> execResponse = executionService.startPipeline(getProgram(), getPipeline())
        .execute();

    if (execResponse.isSuccessful()) {
      logger.println("[SUCCESS] Pipeline was started successfully! You can monitor it's progress in cloud manager.");
    } else {
      throw new IllegalStateException("Pipeline was not started, service responded with status: " +
          execResponse.code() +
          "and error body: "+ execResponse.errorBody().string());
    }
  }

  @Symbol("greet")
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Inject CloudManagerGlobalConfig config;

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Cloud Manager Build Step";
    }

    public ListBoxModel doFillProgramItems() throws Exception {
      ListBoxModel items = new ListBoxModel();
      items.add("Select Program", "");
      ProgramsService service = new ProgramsService(config);
      service
          .getPrograms()
          .execute()
          .body()
          .getEmbedded()
          .getPrograms()
          .stream()
          .forEach(p -> items.add(p.getName() + " (" + p.getId() + ")", p.getId()));
      return items;
    }

    public ListBoxModel doFillPipelineItems(@QueryParameter String program) {
      ListBoxModel items = new ListBoxModel();
      PipelinesService service = new PipelinesService(config);
      if (StringUtils.isBlank(program)) {
        return items;
      }
      try {
        service
            .getPipelines(program)
            .execute()
            .body()
            .getEmbedded()
            .getPipelines()
            .forEach(
                p -> {
                  items.add(p.getName() + " (" + p.getId() + ")", p.getId());
                });
      } catch (IOException e) {
        // do nothing for now
      }
      return items;
    }
  }
}

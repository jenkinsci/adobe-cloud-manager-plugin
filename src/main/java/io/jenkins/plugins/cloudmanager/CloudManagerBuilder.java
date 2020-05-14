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
import hudson.util.Secret;
import io.jenkins.plugins.cloudmanager.client.ProgramsService;
import io.swagger.client.api.ProgramsApi;
import java.io.IOException;
import java.io.PrintStream;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import retrofit2.Retrofit;

public class CloudManagerBuilder extends Builder implements SimpleBuildStep {

  private final String name;

  @DataBoundConstructor
  public CloudManagerBuilder(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  private Secret password;

  @DataBoundSetter
  public void setPassword(Secret password) {
    this.password = password;
  }

  public Secret getPassword() {
    return password;
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws InterruptedException, IOException {
    PrintStream logger = listener.getLogger();
    CloudManagerGlobalConfig config = ExtensionList.lookupSingleton(CloudManagerGlobalConfig.class);
    String accessToken = config.getAccessToken();
    if (StringUtils.isNoneBlank(accessToken)) {
      logger.println("Found already stored access token: " + accessToken);
    } else {
      logger.println("No stored access token, attempting to get a new one");
      accessToken = config.refreshAccessToken();
      if (StringUtils.isNoneBlank(accessToken)) {
        logger.println("Got a spanking new token: " + accessToken);
      } else {
        logger.println("Could not get a new token ;(");
      }
    }

    ProgramsService service = new ProgramsService(config);
    service.getPrograms()
        .execute()
        .body()
        .getEmbedded()
        .getPrograms()
        .stream()
        .forEach(p -> {
          logger.println("got program: " + p.getId() + " " + p.getName());
        });
  }

  @Symbol("greet")
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Cloud Manager Build Step";
    }
  }
}

package io.jenkins.plugins.adobe.cloudmanager.builder;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.adobe.cloudmanager.util.CredentialsUtil;
import jenkins.tasks.SimpleBuildStep;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class CloudManagerRepositorySyncBuilder extends Builder implements SimpleBuildStep {

  private final String url;
  private final String credentialsId;
  private boolean force = false;

  @DataBoundConstructor
  public CloudManagerRepositorySyncBuilder(@Nonnull String url, @Nonnull String credentialsId) {
    this.url = url;
    this.credentialsId = credentialsId;
  }

  public String getUrl() {
    return url;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  /**
   * Whether or not to force the <code>push</code> to Cloud Manager's repository
   *
   * @return force flag
   */
  public boolean isForce() {
    return force;
  }

  @DataBoundSetter
  public void setForce(boolean force) {
    this.force = force;
  }



  private GitSCM findFirst(@Nonnull List<SCM> list, @Nonnull PrintStream log) throws AbortException {

    if (list.isEmpty()) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_missingGitRepository());
    }

    SCM scm = list.get(0);
    if (!(scm instanceof GitSCM)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_notGitRepo());
    }
    GitSCM git = (GitSCM) scm;
    if (list.size() > 1) {
      log.println(Messages.CloudManagerRepositorySyncBuilder_warning_multipleScms(git.getUserRemoteConfigs().get(0).getUrl()));
    }
    return git;
  }

  private GitSCM getScm(@Nonnull WorkflowRun run, @Nonnull PrintStream log) throws AbortException {
    return findFirst(run.getSCMs(), log);
  }

  private GitSCM getScm(@Nonnull AbstractBuild<?, ?> build, @Nonnull PrintStream log) throws AbortException {
    return findFirst(Collections.singletonList(build.getParent().getScm()), log);
  }

  private GitClient createClient(@Nonnull GitSCM scm,
                                 @Nonnull Run<?, ?> run,
                                 @Nonnull FilePath workspace,
                                 @Nonnull EnvVars env,
                                 @Nonnull TaskListener listener) throws IOException, InterruptedException {

    GitClient git = scm.createClient(listener, env, run, workspace);
    if (!git.hasGitRepo(false)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_missingGitRepository());
    }
    return git;
  }

  private StandardUsernameCredentials getCreds() throws AbortException {
    Optional<StandardUsernameCredentials> creds = CredentialsUtil.credentialsFor(credentialsId, StandardUsernameCredentials.class);
    if (!creds.isPresent()) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_missingGitCredentials(credentialsId));
    }
    return creds.get();
  }

  private void push(@Nonnull GitClient client, @Nonnull EnvVars env, @Nonnull PrintStream log) throws AbortException {

    String branch = env.get(GitSCM.GIT_BRANCH, "");
    String sha1 = env.get(GitSCM.GIT_COMMIT, "");
    if (StringUtils.isBlank(branch) || StringUtils.isBlank(sha1)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_missingGitInfo(branch, sha1));
    } else {
      branch = StringUtils.remove(branch, "origin/");
    }

    try {
      log.println(Messages.CloudManagerRepositorySyncBuilder_pushMessage(url));
      client.setCredentials(getCreds());
      client.push().to(new URIish(url)).ref(sha1 + ":refs/heads/" + branch).force(isForce()).execute();
    } catch (URISyntaxException e) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_invalidRemoteRepository(url));
    } catch (GitException | InterruptedException ex) {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_pushFailed(ex.getMessage()));
    }
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run,
                      @Nonnull FilePath workspace,
                      @Nonnull EnvVars env,
                      @Nonnull Launcher launcher,
                      @Nonnull TaskListener listener) throws InterruptedException, IOException {
    PrintStream log = listener.getLogger();

    GitSCM scm;
    if (run instanceof WorkflowRun) {
      scm = getScm((WorkflowRun) run, log);
    } else if (run instanceof AbstractBuild) {
      scm = getScm((AbstractBuild<?, ?>) run, log);
    } else {
      throw new AbortException(Messages.CloudManagerRepositorySyncBuilder_error_invalidRunType());
    }
    scm.buildEnvironment(run, env);
    GitClient client = createClient(scm, run, workspace, env, listener);
    push(client, env, log);
  }

  @Override
  public boolean requiresWorkspace() {
    return true;
  }

  @Symbol("cloudManagerRepoSync")
  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Inject
    private UserRemoteConfig.DescriptorImpl delegate;

    /**
     * Create a list of the credentials that are valid for this Step.
     *
     * @param project       the project context
     * @param url           the Cloud Manager Repository URL
     * @param credentialsId the current credentials id for the Cloud Manager authentication
     * @return list of credentials
     */
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project, @QueryParameter String url, @QueryParameter String credentialsId) {
      return delegate.doFillCredentialsIdItems(project, url, credentialsId);
    }

    /**
     * Check if the provided Cloud Manager url is valid.
     *
     * @param project       the project context
     * @param url           the Cloud Manager Repository URL
     * @param credentialsId the current credentials id for the Cloud Manager authentication
     * @return the form status
     * @throws IOException          if an error occurs
     * @throws InterruptedException if an error occurs
     */
    public FormValidation doCheckUrl(@AncestorInPath Item project, @QueryParameter String url, @QueryParameter String credentialsId) throws IOException, InterruptedException {
      return delegate.doCheckUrl(project, credentialsId, url);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) { return true; }

    @Nonnull
    @Override
    public String getDisplayName() { return Messages.CloudManagerRepositorySyncBuilder_displayName();
    }
  }
}

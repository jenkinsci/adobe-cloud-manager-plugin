package io.jenkins.plugins.adobe.cloudmanager.step;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.adobe.cloudmanager.util.CredentialsUtil;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Pipeline Step to synchronize the current project to a Cloud Manager Git repository.
 */
public class CloudManagerRepositorySyncStep extends Step {

  private final String url;
  private final String credentialsId;
  private boolean force = false;

  /**
   * Create a new Sync Step.
   *
   * @param url           the Cloud Manager repository URL
   * @param credentialsId the credentials ids to use for the Cloud Manager repository
   */
  @DataBoundConstructor
  public CloudManagerRepositorySyncStep(String url, String credentialsId) {
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

  /**
   * Starts this Step
   *
   * @param context the context for the step run
   * @return the execution processes.
   * @throws Exception if any errors occur during the step
   */
  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new StepExecutionImpl(this, context);
  }

  private void push(@Nonnull WorkflowRun run, @Nonnull FilePath workspace, @Nonnull TaskListener listener) throws InterruptedException, IOException {
    PrintStream log = listener.getLogger();

    GitSCM.VERBOSE = true;
    List<SCM> scmList = run.getSCMs();
    if (scmList.isEmpty()) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_missingGitRepository());
    }

    SCM scm = scmList.get(0);

    if (!(scm instanceof GitSCM)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_notGitRepo());
    }
    GitSCM gitSCM = (GitSCM) scm;
    if (scmList.size() > 1) {
      log.println(Messages.CloudManagerRepositorySyncStep_warning_multipleScms(gitSCM.getUserRemoteConfigs().get(0).getUrl()));
    }
    EnvVars env = run.getEnvironment(listener);
    scm.buildEnvironment(run, env);
    String branch = env.get(GitSCM.GIT_BRANCH, "");
    String sha1 = env.get(GitSCM.GIT_COMMIT, "");
    if (StringUtils.isBlank(branch) || StringUtils.isBlank(sha1)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_missingGitInfo(branch, sha1));
    } else {
      branch = StringUtils.remove(branch, "origin/");
    }

    Optional<StandardUsernameCredentials> creds = CredentialsUtil.credentialsFor(credentialsId, StandardUsernameCredentials.class);
    if (!creds.isPresent()) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_missingGitCredentials(credentialsId));
    }

    GitClient git = gitSCM.createClient(listener, env, run, workspace);
    if (!git.hasGitRepo(false)) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_missingGitRepository());
    }
    log.println(Messages.CloudManagerRepositorySyncStep_pushMessage(url));
    try {
      git.setCredentials(creds.get());
      git.push().to(new URIish(url)).ref(sha1 + ":refs/heads/" + branch).force(isForce()).execute();
    } catch (URISyntaxException e) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_invalidRemoteRepository(url));
    } catch (GitException | InterruptedException ex) {
      throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_pushFailed(ex.getMessage()));
    }
  }

  /**
   * Implementation of the Step Execution which which perform the step actions.
   */
  public static final class StepExecutionImpl extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
    private final transient CloudManagerRepositorySyncStep step;

    StepExecutionImpl(CloudManagerRepositorySyncStep step, StepContext context) {
      super(context);
      this.step = step;
    }

    @Override
    protected Void run() throws Exception {
      StepContext ctx = this.getContext();
      Run<?, ?> run = ctx.get(Run.class);
      if (!(run instanceof WorkflowRun)) {
        throw new AbortException(Messages.CloudManagerRepositorySyncStep_error_invalidRunType(WorkflowRun.class.getSimpleName(), run.getClass().getSimpleName()));
      }

      this.step.push(((WorkflowRun) run), ctx.get(FilePath.class), ctx.get(TaskListener.class));
      return null;
    }
  }

  /**
   * Describe how this Step functions and provide a form for the Pipeline syntax help.
   */
  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

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
    public Set<? extends Class<?>> getRequiredContext() {
      HashSet<Class<?>> context = new HashSet<>();
      context.add(Run.class);
      context.add(FilePath.class);
      context.add(TaskListener.class);
      return context;
    }

    @Override
    public String getFunctionName() {
      return "cloudManagerRepoSync";
    }
  }
}

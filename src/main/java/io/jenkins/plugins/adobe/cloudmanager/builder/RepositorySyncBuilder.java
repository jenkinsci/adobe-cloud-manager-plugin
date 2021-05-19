package io.jenkins.plugins.adobe.cloudmanager.builder;

/*-
 * #%L
 * Adobe Cloud Manager Plugin
 * %%
 * Copyright (C) 2020 - 2021 Adobe Inc.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
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

/**
 * Synchronizes the repository associated with this build to the configured Cloud Manager repository.
 */
public class RepositorySyncBuilder extends Builder implements SimpleBuildStep {

  private final String url;
  private final String credentialsId;
  private boolean force = false;

  /**
   * Create an new Repository Sync Builder using the specified Cloud Manager URL and Credentials.
   */
  @DataBoundConstructor
  public RepositorySyncBuilder(@Nonnull String url, @Nonnull String credentialsId) {
    this.url = url;
    this.credentialsId = credentialsId;
  }

  /**
   * The URL to the Cloud Manager git repository.
   */
  @CheckForNull
  public String getUrl() {
    return url;
  }

  /**
   * The credentials id of the secret containing the Cloud Manager git credentials.
   */
  @CheckForNull
  public String getCredentialsId() {
    return credentialsId;
  }

  /**
   * Whether or not to force the <code>push</code> to Cloud Manager's repository
   */
  public boolean isForce() {
    return force;
  }

  /**
   * Set whether or not the sync to Cloud Manager's Git should be forced.
   * <p>
   *   Use with caution, this will overwrite <strong>all</strong> remote content.
   * </p>
   */
  @DataBoundSetter
  public void setForce(boolean force) {
    this.force = force;
  }

  /*
    Get the first SCM from the provided list. Run/Build SCM is required for this builder to work.
   */
  private GitSCM findFirst(@Nonnull List<SCM> list, @Nonnull PrintStream log) throws AbortException {

    if (list.isEmpty()) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_missingGitRepository());
    }

    SCM scm = list.get(0);
    if (!(scm instanceof GitSCM)) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_notGitRepo());
    }
    GitSCM git = (GitSCM) scm;
    if (list.size() > 1) {
      log.println(Messages.RepositorySyncBuilder_warning_multipleScms(git.getUserRemoteConfigs().get(0).getUrl()));
    }
    return git;
  }

  // Workflow jobs could have more than one SCM.
  private GitSCM getScm(@Nonnull WorkflowRun run, @Nonnull PrintStream log) throws AbortException {
    return findFirst(run.getSCMs(), log);
  }

  // Builds only ever(?) have one SCM.
  private GitSCM getScm(@Nonnull AbstractBuild<?, ?> build, @Nonnull PrintStream log) throws AbortException {
    return findFirst(Collections.singletonList(build.getParent().getScm()), log);
  }

  // Creates a Git client for the SCM.
  private GitClient createClient(@Nonnull GitSCM scm,
                                 @Nonnull Run<?, ?> run,
                                 @Nonnull FilePath workspace,
                                 @Nonnull EnvVars env,
                                 @Nonnull TaskListener listener) throws IOException, InterruptedException {

    GitClient git = scm.createClient(listener, env, run, workspace);
    if (!git.hasGitRepo(false)) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_missingGitRepository());
    }
    return git;
  }

  private StandardUsernameCredentials getCreds() throws AbortException {
    Optional<StandardUsernameCredentials> creds = CredentialsUtil.credentialsFor(credentialsId, StandardUsernameCredentials.class);
    if (!creds.isPresent()) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_missingGitCredentials(credentialsId));
    }
    return creds.get();
  }

  // The actual work of syncing to Cloud Manager's git.
  private void push(@Nonnull GitClient client, @Nonnull EnvVars env, @Nonnull PrintStream log) throws AbortException {

    String branch = env.get(GitSCM.GIT_BRANCH, "");
    String sha1 = env.get(GitSCM.GIT_COMMIT, "");
    if (StringUtils.isBlank(branch) || StringUtils.isBlank(sha1)) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_missingGitInfo(branch, sha1));
    } else {
      branch = StringUtils.remove(branch, "origin/");
    }

    try {
      log.println(Messages.RepositorySyncBuilder_pushMessage(url));
      client.setCredentials(getCreds());
      client.push().to(new URIish(url)).ref(sha1 + ":refs/heads/" + branch).force(isForce()).execute();
    } catch (URISyntaxException e) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_invalidRemoteRepository(url));
    } catch (GitException | InterruptedException e) {
      throw new AbortException(Messages.RepositorySyncBuilder_error_pushFailed(e.getLocalizedMessage()));
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
      throw new AbortException(Messages.RepositorySyncBuilder_error_invalidRunType());
    }
    scm.buildEnvironment(run, env);
    GitClient client = createClient(scm, run, workspace, env, listener);
    push(client, env, log);
  }

  // Sync requires a workspace, as the client needs the files to push up to Cloud Manager remote.
  @Override
  public boolean requiresWorkspace() {
    return true;
  }

  @Symbol("acmRepoSync")
  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Inject // Can this be replaced with a lookup?
    private UserRemoteConfig.DescriptorImpl delegate;

    /**
     * Create a list of the credentials that are valid for this Step.
     */
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project, @QueryParameter String url, @QueryParameter String credentialsId) {
      return delegate.doFillCredentialsIdItems(project, url, credentialsId);
    }

    /**
     * Check if the provided Cloud Manager url is valid.
     */
    public FormValidation doCheckUrl(@AncestorInPath Item project, @QueryParameter String url, @QueryParameter String credentialsId) throws IOException, InterruptedException {
      return delegate.doCheckUrl(project, credentialsId, url);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Nonnull
    @Override
    public String getDisplayName() { return Messages.RepositorySyncBuilder_displayName();
    }
  }
}

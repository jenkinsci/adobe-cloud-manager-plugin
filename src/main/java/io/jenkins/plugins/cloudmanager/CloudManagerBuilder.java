package io.jenkins.plugins.cloudmanager;

import hudson.ExtensionList;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.Secret;
import java.util.Optional;
import jenkins.security.ConfidentialStore;
import jenkins.security.DefaultConfidentialStore;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

public class CloudManagerBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private boolean useFrench;

    @DataBoundConstructor
    public CloudManagerBuilder(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isUseFrench() {
        return useFrench;
    }

    private Secret password;

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
    }

    public Secret getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setUseFrench(boolean useFrench) {
        this.useFrench = useFrench;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        if (useFrench) {
            listener.getLogger().println("Bonjour, " + name + "!");
        } else {
            listener.getLogger().println("Hello, " + name + "!");
        }
        listener.getLogger().println("your password is, " + password.getPlainText());
        CloudManagerGlobalConfig config = ExtensionList.lookupSingleton(CloudManagerGlobalConfig.class);
        if (null != config) {
            listener.getLogger().println( "apiKey: " + config.getApiKey() + "\n" +
                " organizationID: "+ config.getOrganizationID() + "\n" +
                " technicalAccountId" +  config.getTechnicalAccountId() + "\n" +
                " clientSecret" + Optional.ofNullable(config.getClientSecret()).map(Secret::getPlainText).orElse(null) + "\n" +
                " privateKey:" + Optional.ofNullable(config.getPrivateKey()).map(Secret::getPlainText).orElse(null) +"\n");
        }

    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("missing name");
            if (value.length() < 4)
                return FormValidation.warning("too short");
            if (!useFrench && value.matches(".*[éáàç].*")) {
                return FormValidation.warning("are you french");
            }
            return FormValidation.ok();
        }

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

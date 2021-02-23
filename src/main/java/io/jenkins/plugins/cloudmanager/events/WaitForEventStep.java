package io.jenkins.plugins.cloudmanager.events;

import java.util.Collection;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;

/**
 * Implements a custom pipeline step that waits for an Adobe IO event to be received at the webhook endpoint
 * {@link WebhookRootAction}
 * 
 * @see <a href="https://github.com/jenkinsci/workflow-step-api-plugin/blob/master/README.md">Step API</a>
 *
 */
public class WaitForEventStep extends Step {

    private Collection<String> types;
    private Collection<String> objectTypes;
    private String organizationId;
    private String clientSecret;

    @DataBoundConstructor
    public WaitForEventStep(String organizationId, String clientSecret) {
        this.organizationId = organizationId;
        this.clientSecret = clientSecret;
    }
    
    @DataBoundSetter
    public void setTypes(Collection<String> types) {
        this.types = types;
    }

    @DataBoundSetter
    public void setObjectTypes(Collection<String> objectTypes) {
        this.objectTypes = objectTypes;
    }
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        EventSelector eventSelector = new EventSelector(organizationId, types, objectTypes);
        return new WaitForEventExecution(context, eventSelector, clientSecret);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "waitForAioEvent";
        }

        @Override
        public String getDisplayName() {
            return "Waits until the webhook endpoint has received an Adobe IO event of the requested type";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of();
        }
    }
}

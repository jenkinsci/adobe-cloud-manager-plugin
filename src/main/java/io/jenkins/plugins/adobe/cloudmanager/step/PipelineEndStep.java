package io.jenkins.plugins.adobe.cloudmanager.step;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class PipelineEndStep extends Step {

  private boolean mirror = true;

  @DataBoundConstructor
  public PipelineEndStep() {

  }

  public boolean isMirror() {
    return mirror;
  }

  @DataBoundSetter
  public void setMirror(boolean mirror) {
    this.mirror = mirror;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new PipelineEndExecution(context, mirror);
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "acmPipelineEnd";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, FlowNode.class, TaskListener.class)));
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.PipelineEndStep_displayName();
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

  }
}

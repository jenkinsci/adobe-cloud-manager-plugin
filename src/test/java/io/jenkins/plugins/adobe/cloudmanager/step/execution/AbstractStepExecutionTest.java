package io.jenkins.plugins.adobe.cloudmanager.step.execution;

import hudson.AbortException;
import hudson.model.Run;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.jenkins.plugins.adobe.cloudmanager.action.CloudManagerBuildAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AbstractStepExecutionTest {

  private static final String found = "Found Config";

  @Tested
  private AbstractStepExecution tested;

  @Injectable
  private StepContext context;

  @Mocked
  private Run<?, ?> run;

  @Mocked
  private CloudManagerBuildAction data;

  @Mocked
  private CloudManagerApi api;

  @Mocked
  private AdobeIOProjectConfig projectConfig;

  @Before
  public void before() throws Exception {
    new MockUp<AdobeIOConfig>() {
      @Mock
      public AdobeIOProjectConfig projectConfigFor(String name) {
        return found.equals(name) ? projectConfig : null;
      }
    };
    new Expectations() {{
      context.get(Run.class);
      result = run;
      minTimes = 0;
      run.getAction(CloudManagerBuildAction.class);
      result = data;
      minTimes = 0;
    }};
  }

  @Test
  public void validateMissingAIOProject() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingProgramId() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingPipelineId() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateMissingExecutionId() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
      data.getPipelineId();
      result = "Pipeline Id";
    }};
    assertThrows(AbortException.class, () -> tested.validateData());
  }

  @Test
  public void validateSuccess() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      data.getProgramId();
      result = "Program Id";
      data.getPipelineId();
      result = "Pipeline Id";
      data.getExecutionId();
      result = "Execution Id";
    }};
    tested.validateData();
  }

  @Test
  public void projectMissing() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = "Missing";
    }};
    assertThrows(AbortException.class, () -> tested.getAioProject());
  }

  @Test
  public void projectFound() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
    }};
    assertNotNull(tested.getAioProject());
  }

  @Test
  public void accessTokenFails() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      projectConfig.authenticate();
      result = null;
    }};
    assertThrows(AbortException.class, () -> tested.getAccessToken());
  }

  @Test
  public void accessTokenSuccess() throws Exception {
    new Expectations() {{
      data.getAioProjectName();
      result = found;
      projectConfig.authenticate();
      result = Secret.fromString("Test");
    }};
    assertNotNull(tested.getAccessToken());
  }
}

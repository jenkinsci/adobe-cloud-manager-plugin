package io.jenkins.plugin.adobe.cloudmanager.project;

import java.util.ArrayList;
import java.util.List;

import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.Pipeline;
import io.adobe.cloudmanager.Program;
import io.jenkins.plugin.adobe.cloudmanager.util.DescriptorHelperTest;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.project.CloudManagerProjectProperty;
import mockit.Mocked;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class CloudManagerProjectPropertyTest {

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  @Mocked
  private CloudManagerApi api;

  @Test
  public void roundTrip() throws Exception {
    setupAdobeIOConfigs(rule.jenkins);
    AdobeIOProjectConfig aioConfig = AIO_PROJECT_CONFIGS.get(0);
    List<Program> programs = new ArrayList<>();
    programs.add(new DescriptorHelperTest.ProgramImpl("1", "Program"));

    List<Pipeline> pipelines = new ArrayList<>();
    pipelines.add(new DescriptorHelperTest.PipelineImpl("2", "Pipeline"));

    WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "test");
    rule.configRoundtrip(job);
    assertNull(job.getProperty(CloudManagerProjectProperty.class));

    job.addProperty(new CloudManagerProjectProperty(AIO_PROJECT_NAME, "1", "2"));
    CloudManagerProjectProperty property = job.getProperty(CloudManagerProjectProperty.class);
    assertNotNull(property);
    assertEquals(AIO_PROJECT_NAME, property.getAioProject());
    assertEquals("1", property.getProgram());
    assertEquals("2", property.getPipeline());

    DescribableModel<CloudManagerProjectProperty> dm = DescribableModel.of(CloudManagerProjectProperty.class);
    property = dm.instantiate(dm.uninstantiate2(property).getArguments(), null);
    assertEquals(AIO_PROJECT_NAME, property.getAioProject());
    assertEquals("1", property.getProgram());
    assertEquals("2", property.getPipeline());
  }
}


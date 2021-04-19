package io.jenkins.plugin.adobe.cloudmanager.config;

import java.util.List;

import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigAsCodeTest {

  @Rule
  public JenkinsConfiguredWithCodeRule rule = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("configuration-as-code.yaml")
  public void shouldSupportConfigurationAsCode() {
    AdobeIOConfig aioconfig = AdobeIOConfig.all().get(AdobeIOConfig.class);

    assertEquals("Configurations were loaded", 2, aioconfig.getProjectConfigs().size());

    AdobeIOProjectConfig config = aioconfig.getProjectConfigs().get(0);
    assertEquals("Name is correct.", "Test Project 1", config.getName());
    assertEquals("API URL is correct.", AdobeIOProjectConfig.ADOBE_IO_URL, config.getApiUrl());
    assertEquals("Client Id is correct", "Client Id 1", config.getClientId());
    assertEquals("IMS Org is correct", "Ims Organization Id 1", config.getImsOrganizationId());
    assertEquals("Tech Account is correct", "Technical Account Id 1", config.getTechnicalAccountId());
    assertEquals("Client Secret Cred is correct", "Client Secret Credentials Id 1", config.getClientSecretCredentialsId());
    assertEquals("Private Key Cred is correct", "Private Key Credentials Id 1", config.getPrivateKeyCredentialsId());
    assertTrue("Validate Signatures is correct.", config.isValidateSignatures());

    config = aioconfig.getProjectConfigs().get(1);
    assertEquals("Name is correct.", "Test Project 2", config.getName());
    assertEquals("API URL is correct.", "http://notdefault.adobe.io/url", config.getApiUrl());
    assertEquals("Client Id is correct", "Client Id 2", config.getClientId());
    assertEquals("IMS Org is correct", "Ims Organization Id 2", config.getImsOrganizationId());
    assertEquals("Tech Account is correct", "Technical Account Id 2", config.getTechnicalAccountId());
    assertEquals("Client Secret Cred is correct", "Client Secret Credentials Id 2", config.getClientSecretCredentialsId());
    assertEquals("Private Key Cred is correct", "Private Key Credentials Id 2", config.getPrivateKeyCredentialsId());
    assertFalse("Validate Signatures is correct.", config.isValidateSignatures());
  }

  @Test
  @ConfiguredWithCode("configuration-as-code.yaml")
  public void exportConfiguration() throws Exception {
    AdobeIOConfig aioconfig = AdobeIOConfig.all().get(AdobeIOConfig.class);
    ConfiguratorRegistry registry = ConfiguratorRegistry.get();
    ConfigurationContext context = new ConfigurationContext(registry);
    Configurator<AdobeIOConfig> configurator = context.lookupOrFail(AdobeIOConfig.class);

    CNode node = configurator.describe(aioconfig, context);
    assertNotNull(node);
    Mapping mapping = node.asMapping();
    CNode configsNode = mapping.get("projectConfigs");

    @SuppressWarnings("unchecked")
    List<Mapping> configs = (List) configsNode.asSequence();
    assertEquals(2, configs.size());
    assertEquals("Project Name is correct.", "Test Project 1", configs.get(0).getScalarValue("name"));
    assertEquals("Project Name is correct.", "Test Project 2", configs.get(1).getScalarValue("name"));
  }

}

package io.jenkins.plugin.adobe.cloudmanager.config;

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
    AdobeIOConfig aioconfig = AdobeIOConfig.configuration();

    assertEquals("Configurations were loaded", 2, aioconfig.getProjectConfigs().size());

    AdobeIOProjectConfig config = aioconfig.getProjectConfigs().get(0);
    assertEquals("Name is correct.", "Test Project 1", config.getName());
    assertEquals("API URL is correct.", AdobeIOProjectConfig.ADOBE_IO_URL, config.getApiUrl());
    assertEquals("Client Id is correct", "Client Id 1", config.getClientId());
    assertEquals("IMS Org is correct", "Ims Organization Id 1", config.getImsOrganizationId());
    assertEquals("Tech Account is correct", "Technical Account Id 1", config.getTechnicalAccountId());
    assertEquals("Client Secret Cred is correct", "Client Secret Credentials Id 1", config.getClientSecretCredentialsId());
    assertEquals("Private Key Cred is correct", "Private Key Credentials Id 1", config.getPrivateKeyCredentialsId());

    config = aioconfig.getProjectConfigs().get(1);
    assertEquals("Name is correct.", "Test Project 2", config.getName());
    assertEquals("API URL is correct.", "http://notdefault.adobe.io/url", config.getApiUrl());
    assertEquals("Client Id is correct", "Client Id 2", config.getClientId());
    assertEquals("IMS Org is correct", "Ims Organization Id 2", config.getImsOrganizationId());
    assertEquals("Tech Account is correct", "Technical Account Id 2", config.getTechnicalAccountId());
    assertEquals("Client Secret Cred is correct", "Client Secret Credentials Id 2", config.getClientSecretCredentialsId());
    assertEquals("Private Key Cred is correct", "Private Key Credentials Id 2", config.getPrivateKeyCredentialsId());
  }

  @Test
  @ConfiguredWithCode("configuration-as-code.yaml")
  public void exportConfiguration() throws Exception {
    AdobeIOConfig aioconfig = AdobeIOConfig.configuration();
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

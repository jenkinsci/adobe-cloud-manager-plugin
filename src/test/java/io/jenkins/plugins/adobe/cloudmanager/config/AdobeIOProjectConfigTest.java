package io.jenkins.plugins.adobe.cloudmanager.config;

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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import hudson.util.Secret;
import io.adobe.cloudmanager.AdobeClientCredentials;
import io.adobe.cloudmanager.IdentityManagementApi;
import io.adobe.cloudmanager.IdentityManagementApiException;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class AdobeIOProjectConfigTest {

  @Rule
  public JenkinsRule rule = new JenkinsRule();
  @Mocked
  private IdentityManagementApi imApi;

  private AdobeClientCredentials creds = new AdobeClientCredentials(IMS_ORG_ID, TECH_ACCT_ID, CLIENT_ID, CLIENT_SECRET, privateKey);

  @Before
  public void before() throws Exception {
    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
  }

  @Test
  public void shouldMatchDisplayName() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName("Test Name");
    config.setImsOrganizationId("IMS Org Id");
    assertEquals("Test Name (IMS Org Id)", config.getDisplayName());
  }

  @Test
  public void authenticateMissingPrivateKey() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName("Test Name");
    config.setPrivateKeyCredentialsId("MISSING");
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateBadPrivateKey() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName("Test Name");
    config.setPrivateKeyCredentialsId(PUBLIC_KEY_CRED_ID);
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateMissingClientSecret() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName("Test Name");
    config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
    config.setClientSecretCredentialsId("MISSING");
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateApiError() throws Exception {
    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create(String baseUrl) {
        assertEquals(AdobeIOProjectConfig.ADOBE_IO_URL, baseUrl);
        return imApi;
      }
    };

    new Expectations() {{
      imApi.authenticate(withEqual(creds));
      result = new IdentityManagementApiException("Authentication Failed", null);
    }};

    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateSuccessNewToken() throws Exception {

    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create(String baseUrl) {
        assertEquals(AdobeIOProjectConfig.ADOBE_IO_URL, baseUrl);
        return imApi;
      }
    };

    new Expectations() {{
      imApi.authenticate(withEqual(creds));
      result = ACCESS_TOKEN;
    }};
    String hash = Integer.toString(IMS_ORG_ID.hashCode());
    String configId = AIO_PROJECT_NAME.replaceAll("[^a-zA-Z0-9_.-]+", "").concat("-").concat(hash);
    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNotNull(result);
    assertEquals(ACCESS_TOKEN, result.getPlainText());
    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    List<Credentials> credentialsList = store.getCredentials(aioDomain);
    Credentials found = credentialsList.stream().filter(c -> ((c instanceof StringCredentials) && configId.equals(((StringCredentials) c).getId()))).findFirst().orElse(null);
    assertNotNull(found);
    assertEquals(ACCESS_TOKEN, ((StringCredentials) found).getSecret().getPlainText());
  }

  @Test
  public void authenticateSuccessExistingToken() throws Exception {
    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create(String baseUrl) {
        assertEquals(AdobeIOProjectConfig.ADOBE_IO_URL, baseUrl);
        return imApi;
      }
    };

    new Expectations() {{
      imApi.isValid(withEqual(creds), ACCESS_TOKEN);
      result = true;
    }};
    String hash = Integer.toString(IMS_ORG_ID.hashCode());
    String configId = AIO_PROJECT_NAME.replaceAll("[^a-zA-Z0-9_.-]+", "").concat("-").concat(hash);
    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    store.addCredentials(aioDomain, new StringCredentialsImpl(CredentialsScope.SYSTEM, configId,"", Secret.fromString(ACCESS_TOKEN)));
    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNotNull(result);
    assertEquals(ACCESS_TOKEN, result.getPlainText());
  }

  @Test
  public void validateApiError() throws Exception {
    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create(String baseUrl) {
        assertEquals(AdobeIOProjectConfig.ADOBE_IO_URL, baseUrl);
        return imApi;
      }
    };

    new Expectations() {{
      imApi.isValid(withEqual(creds), ACCESS_TOKEN);
      result = new IdentityManagementApiException("Authentication Failed", null);
      imApi.authenticate(withEqual(creds));
      result = new IdentityManagementApiException("Authentication Failed", null);
    }};

    String hash = Integer.toString(IMS_ORG_ID.hashCode());
    String configId = AIO_PROJECT_NAME.replaceAll("[^a-zA-Z0-9_.-]+", "").concat("-").concat(hash);

    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    store.addCredentials(aioDomain, new StringCredentialsImpl(CredentialsScope.SYSTEM, configId,"", Secret.fromString(ACCESS_TOKEN)));
    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateSuccessExpiredToken() throws Exception {
    String newAccessToken = "New Access Token";
    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create(String baseUrl) {
        assertEquals(AdobeIOProjectConfig.ADOBE_IO_URL, baseUrl);
        return imApi;
      }
    };

    new Expectations() {{
      imApi.isValid(withEqual(creds), ACCESS_TOKEN);
      result = false;
      imApi.authenticate(withEqual(creds));
      result = newAccessToken;
    }};
    String hash = Integer.toString(IMS_ORG_ID.hashCode());
    String configId = AIO_PROJECT_NAME.replaceAll("[^a-zA-Z0-9_.-]+", "").concat("-").concat(hash);
    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    store.addCredentials(aioDomain, new StringCredentialsImpl(CredentialsScope.SYSTEM, configId,"", Secret.fromString(ACCESS_TOKEN)));

    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNotNull(result);
    assertEquals(newAccessToken, result.getPlainText());
    List<Credentials> credentialsList = store.getCredentials(aioDomain);
    Credentials found = credentialsList.stream().filter(c -> ((c instanceof StringCredentials) && configId.equals(((StringCredentials) c).getId()))).findFirst().orElse(null);
    assertNotNull(found);
    assertEquals(newAccessToken, ((StringCredentials) found).getSecret().getPlainText());

  }
}

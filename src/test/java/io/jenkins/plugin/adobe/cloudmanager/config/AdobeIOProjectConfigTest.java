package io.jenkins.plugin.adobe.cloudmanager.config;

/*

MIT License

Copyright (c) 2020 Adobe Inc

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

 */

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import hudson.util.Secret;
import io.adobe.cloudmanager.AdobeClientCredentials;
import io.adobe.cloudmanager.IdentityManagementApi;
import io.adobe.cloudmanager.IdentityManagementApiException;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

public class AdobeIOProjectConfigTest {


  private static final String AIO_PROJECT_NAME = "AdobeIO Project";
  private static final String IMS_ORG_ID = "1234567890@AdobeOrg";
  private static final String CLIENT_ID = "1234567890abcdef0987654321";
  private static final String TECH_ACCT_ID = "1234567890abcdef0987654321@techacct.adobe.com";
  private static final String CLIENT_SECRET = "Client Secret";
  private static PrivateKey privateKey;
  private static PublicKey publicKey;
  private static final String CLIENT_SECRET_CRED_ID = "client-secret";
  private static final String PRIVATE_KEY_CRED_ID = "private-key";
  public static final String PUBLIC_KEY_CRED_ID = "public-key";

  private static final String ACCESS_TOKEN = "Secret Access Token";


  @Mocked
  private IdentityManagementApi imApi;

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  public static List<AdobeIOProjectConfig> configs;

  @BeforeClass
  public static void beforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();
    privateKey = kp.getPrivate();
    publicKey = kp.getPublic();

    configs = new ArrayList<>();
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setName(AIO_PROJECT_NAME);
    config.setImsOrganizationId(IMS_ORG_ID);
    config.setClientId(CLIENT_ID);
    config.setTechnicalAccountId(TECH_ACCT_ID);
    config.setClientSecretCredentialsId(CLIENT_SECRET_CRED_ID);
    config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
    configs.add(config);

    config = new AdobeIOProjectConfig();
    config.setName("Another AdobeIO Project");
    config.setImsOrganizationId("IMS Org");
    configs.add(config);
  }

  @Before
  public void before() throws Exception {
    AdobeIOConfig adobeIOConfig = AdobeIOConfig.configuration();
    adobeIOConfig.setProjectConfigs(configs);
    setupCredentials();
  }

  private void setupCredentials() throws Exception {
    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    DomainSpecification ds = new HostnameSpecification(AdobeIOProjectConfig.ADOBE_IO_DOMAIN, null);
    List<DomainSpecification> specifications = new ArrayList<>();
    specifications.add(ds);
    Domain domain = new Domain("AdobeIO", null, specifications);
    store.addDomain(domain);

    Credentials credentials = new StringCredentialsImpl(CredentialsScope.SYSTEM, CLIENT_SECRET_CRED_ID, "", Secret.fromString(CLIENT_SECRET));
    store.addCredentials(domain, credentials);

    String pk = Base64.getEncoder().encodeToString(privateKey.getEncoded());
    credentials = new FileCredentialsImpl(CredentialsScope.SYSTEM, PRIVATE_KEY_CRED_ID, "", "private.key", SecretBytes.fromBytes(pk.getBytes()));
    store.addCredentials(domain, credentials);

    pk = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    credentials = new FileCredentialsImpl(CredentialsScope.SYSTEM, PUBLIC_KEY_CRED_ID, "", "public.key", SecretBytes.fromBytes(pk.getBytes()));
    store.addCredentials(domain, credentials);
  }

  @Test
  public void shouldMatchDisplayName() {
    AdobeIOProjectConfig cfg = new AdobeIOProjectConfig();
    cfg.setName("Test Name");
    cfg.setImsOrganizationId("IMS Org Id");
    assertEquals("Test Name (IMS Org Id)", cfg.getDisplayName());
  }

  @Test
  public void authenticateMissingPrivateKey() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setPrivateKeyCredentialsId("MISSING");
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateBadPrivateKey() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setPrivateKeyCredentialsId(PUBLIC_KEY_CRED_ID);
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateMissingClientSecret() {
    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
    config.setClientSecretCredentialsId("MISSING");
    Secret result = config.authenticate();
    assertNull(result);
  }

  @Test
  public void authenticateApiError() throws Exception {
    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create() { return imApi; }
    };

    new Expectations() {{
      imApi.authenticate(withInstanceOf(AdobeClientCredentials.class));
      result = new IdentityManagementApiException("Authentication Failed", null);
    }};

    AdobeIOProjectConfig config = new AdobeIOProjectConfig();
    config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
    config.setClientSecretCredentialsId(CLIENT_SECRET_CRED_ID);
    Secret result = config.authenticate();
    assertNull(result);
  }


  @Test
  public void authenticateSuccess() throws Exception {
    AdobeClientCredentials creds = new AdobeClientCredentials(IMS_ORG_ID, TECH_ACCT_ID, CLIENT_ID, CLIENT_SECRET, privateKey);

    new MockUp<IdentityManagementApi>() {
      @Mock
      public IdentityManagementApi create() { return imApi; }
    };

    new Expectations() {{
      imApi.authenticate(withEqual(creds));
      result = ACCESS_TOKEN;
    }};

    Secret result = AdobeIOConfig.projectConfigFor(AIO_PROJECT_NAME).authenticate();
    assertNotNull(result);
    assertEquals(ACCESS_TOKEN, result.getPlainText());
  }
}

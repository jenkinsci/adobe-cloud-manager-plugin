package io.jenkins.plugin.adobe.cloudmanager.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
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
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

public class TestHelper {

  public static final String AIO_PROJECT_NAME = "AdobeIO Project";
  public static final String IMS_ORG_ID = "1234567890@AdobeOrg";
  public static final String CLIENT_ID = "1234567890abcdef0987654321";
  public static final String TECH_ACCT_ID = "1234567890abcdef0987654321@techacct.adobe.com";
  public static final String CLIENT_SECRET = "Client Secret";
  public static PrivateKey privateKey;
  public static final String CLIENT_SECRET_CRED_ID = "client-secret";
  public static final String PRIVATE_KEY_CRED_ID = "private-key";

  public static final String ACCESS_TOKEN = "Secret Access Token";

  public static List<AdobeIOProjectConfig> AIO_PROJECT_CONFIGS;

  static {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair kp = kpg.generateKeyPair();
      privateKey = kp.getPrivate();

      AIO_PROJECT_CONFIGS = new ArrayList<>();
      AdobeIOProjectConfig config = new AdobeIOProjectConfig();
      config.setName(AIO_PROJECT_NAME);
      config.setImsOrganizationId(IMS_ORG_ID);
      config.setClientId(CLIENT_ID);
      config.setTechnicalAccountId(TECH_ACCT_ID);
      config.setClientSecretCredentialsId(CLIENT_SECRET_CRED_ID);
      config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
      AIO_PROJECT_CONFIGS.add(config);


      config = new AdobeIOProjectConfig();
      config.setName("Another AdobeIO Project");
      config.setImsOrganizationId("IMS Org");
      AIO_PROJECT_CONFIGS.add(config);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  public static void setupAdobeIOConfigs(@SuppressWarnings("unused") Jenkins jenkins) {
    AdobeIOConfig adobeIOConfig = AdobeIOConfig.configuration();
    adobeIOConfig.setProjectConfigs(AIO_PROJECT_CONFIGS);

  }

  public static void setupCredentials(Jenkins jenkins) throws Exception {
    CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
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
  }
}

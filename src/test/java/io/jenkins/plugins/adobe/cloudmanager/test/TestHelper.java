package io.jenkins.plugin.adobe.cloudmanager.test;

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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
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
  public static PublicKey publicKey;
  public static final String CLIENT_SECRET_CRED_ID = "client-secret";
  public static final String PRIVATE_KEY_CRED_ID = "private-key";
  public static final String PUBLIC_KEY_CRED_ID = "private-key";

  public static final String ACCESS_TOKEN = "Secret Access Token";

  public static List<AdobeIOProjectConfig> AIO_PROJECT_CONFIGS;

  public static Domain aioDomain;

  static {
    try {

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair kp = kpg.generateKeyPair();
      privateKey = kp.getPrivate();
      publicKey = kp.getPublic();

      AIO_PROJECT_CONFIGS = new ArrayList<>();
      AdobeIOProjectConfig config = new AdobeIOProjectConfig();
      config.setName(AIO_PROJECT_NAME);
      config.setImsOrganizationId(IMS_ORG_ID);
      config.setClientId(CLIENT_ID);
      config.setTechnicalAccountId(TECH_ACCT_ID);
      config.setClientSecretCredentialsId(CLIENT_SECRET_CRED_ID);
      config.setPrivateKeyCredentialsId(PRIVATE_KEY_CRED_ID);
      config.setApiUrl(Jenkins.get().getRootUrl());
      AIO_PROJECT_CONFIGS.add(config);


      config = new AdobeIOProjectConfig();
      config.setName("Another AdobeIO Project");
      config.setImsOrganizationId("IMS Org");
      AIO_PROJECT_CONFIGS.add(config);
      DomainSpecification ds = new HostnameSpecification(AdobeIOProjectConfig.ADOBE_IO_DOMAIN, null);

      List<DomainSpecification> specifications = new ArrayList<>();
      specifications.add(ds);
      aioDomain = new Domain("AdobeIO", null, specifications);

    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  public static void setupAdobeIOConfigs(@SuppressWarnings("unused") Jenkins jenkins) {
    AdobeIOConfig adobeIOConfig = AdobeIOConfig.configuration();
    adobeIOConfig.setWebhookEnabled(true);
    adobeIOConfig.setProjectConfigs(AIO_PROJECT_CONFIGS);
  }

  public static void setupCredentials(Jenkins jenkins) throws Exception {
    CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
    store.addDomain(aioDomain);

    Credentials credentials = new StringCredentialsImpl(CredentialsScope.SYSTEM, CLIENT_SECRET_CRED_ID, "", Secret.fromString(CLIENT_SECRET));
    store.addCredentials(aioDomain, credentials);

    String pk = Base64.getEncoder().encodeToString(privateKey.getEncoded());
    credentials = new FileCredentialsImpl(CredentialsScope.SYSTEM, PRIVATE_KEY_CRED_ID, "", "private.key", SecretBytes.fromBytes(pk.getBytes()));
    store.addCredentials(aioDomain, credentials);

    pk = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    credentials = new FileCredentialsImpl(CredentialsScope.SYSTEM, PUBLIC_KEY_CRED_ID, "", "public.key", SecretBytes.fromBytes(pk.getBytes()));
    store.addCredentials(aioDomain, credentials);
  }
}

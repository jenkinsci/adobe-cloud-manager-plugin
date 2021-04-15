package io.jenkins.plugin.adobe.cloudmanager.config;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.util.Secret;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.model.Jenkins;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/*
  Modeled after GitHubServerConfigIntegrationTest.
 */
@For(AdobeIOProjectConfig.class)
public class AdobeIOProjectConfigIntegrationTest {

  private final String clientSecret = "Client Secret";
  @Rule
  public JenkinsRule rule = new JenkinsRule();
  private Server server;
  private AttackerServlet attackerServlet;
  private String attackerUrl;
  private String clientSecretCredId = "client-secret";
  private String privateKeyCredId = "private-key";
  private PrivateKey privateKey;
  private PublicKey publicKey;

  @Before
  public void setup() throws Exception {
    setupAttacker();
    createKeys();
  }

  private void setupAttacker() throws Exception {
    server = new Server();
    ServerConnector connector = new ServerConnector(this.server);
    server.addConnector(connector);
    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    handler.setContextPath("/*");

    attackerServlet = new AttackerServlet();
    ServletHolder holder = new ServletHolder(attackerServlet);
    handler.addServlet(holder, "/*");
    server.setHandler(handler);
    server.start();

    String host = connector.getHost() == null ? "localhost" : connector.getHost();
    this.attackerUrl = "http://" + host + ":" + connector.getLocalPort();
  }

  private void createKeys() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();
    privateKey = kp.getPrivate();
    publicKey = kp.getPublic();
  }

  private void setupCredentials() throws Exception {
    CredentialsStore store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    DomainSpecification ds = new HostnameSpecification(AdobeIOProjectConfig.ADOBE_IO_DOMAIN, null);
    List<DomainSpecification> specifications = new ArrayList<>();
    specifications.add(ds);
    Domain domain = new Domain("AdobeIO", null, specifications);
    store.addDomain(domain);

    Credentials credentials = new StringCredentialsImpl(CredentialsScope.SYSTEM, clientSecretCredId, "", Secret.fromString(clientSecret));
    store.addCredentials(domain, credentials);

    String pk = Base64.getEncoder().encodeToString(privateKey.getEncoded());
    credentials = new FileCredentialsImpl(CredentialsScope.SYSTEM, privateKeyCredId, "", "private.key", SecretBytes.fromBytes(pk.getBytes()));
    store.addCredentials(domain, credentials);
  }

  @Test
  public void shouldNotAllow_CredentialsLeakage_usingVerifyCredentials() throws Exception {
    setupCredentials();
    final URL url = new URL(
        rule.getURL() +
            "descriptorByName/io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig/verifyCredentials?" +
            "apiUrl=" + attackerUrl +
            "&imsOrganizationId=imsOrganizationId" +
            "&technicalAccountId=technicalAccountId" +
            "&clientId=clientId" +
            "&clientSecretCredentialsId=" + clientSecretCredId +
            "&privateKeyCredentialsId=" + privateKeyCredId
    );

    rule.jenkins.setCrumbIssuer(null);
    rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());

    GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
    strategy.add(Jenkins.ADMINISTER, "admin");
    strategy.add(Jenkins.READ, "user");
    rule.jenkins.setAuthorizationStrategy(strategy);

    {// Read Only User
      JenkinsRule.WebClient wc = rule.createWebClient();
      wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
      wc.login("user");

      Page page = wc.getPage(new WebRequest(url, HttpMethod.POST));
      assertEquals(403, page.getWebResponse().getStatusCode());
      assertTrue(StringUtils.isEmpty(attackerServlet.clientSecret));
    }
    { // Admin Can verify
      JenkinsRule.WebClient wc = rule.createWebClient();
      wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
      wc.login("admin");

      Page page = wc.getPage(new WebRequest(url, HttpMethod.POST));
      assertEquals(200, page.getWebResponse().getStatusCode());
      assertTrue(StringUtils.isNotEmpty(attackerServlet.clientSecret));
      assertEquals(clientSecret, attackerServlet.clientSecret);
      attackerServlet.clientSecret = null;
    }
    { // Admin Must use POST
      JenkinsRule.WebClient wc = rule.createWebClient();
      wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
      wc.login("admin");

      Page page = wc.getPage(new WebRequest(url, HttpMethod.GET));
      assertNotEquals(200, page.getWebResponse().getStatusCode());
      assertTrue(StringUtils.isEmpty(attackerServlet.clientSecret));
    }
  }

  private static class AttackerServlet extends DefaultServlet {

    public String clientSecret;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      String body = IOUtils.toString(request.getInputStream(), Charset.defaultCharset());
      String[] pairs = body.split("&");
      Map<String, String> params = new HashMap<>();
      for (String s : pairs) {
        String[] fields = s.split("=");
        String name = URLDecoder.decode(fields[0], "UTF-8");
        String value = URLDecoder.decode(fields[1], "UTF-8");
        params.put(name, value);
      }

      clientSecret = params.get("client_secret");
    }
  }
}

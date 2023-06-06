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

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import io.jenkins.plugins.adobe.cloudmanager.test.TestHelper;
import jenkins.model.Jenkins;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

/*
  Modeled after GitHubServerConfigIntegrationTest.
 */
@For(AdobeIOProjectConfig.class)
public class AdobeIOProjectConfigIntegrationTest {

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  private Server server;
  private AttackerServlet attackerServlet;
  private String attackerUrl;

  @Before
  public void before() throws Exception {
    setupAttacker();
    TestHelper.setupAdobeIOConfigs(rule.jenkins);
  }

  @After
  public void after() throws Exception {
    server.stop();
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

  @Test
  public void shouldNotAllow_CredentialsLeakage_usingVerifyCredentials() throws Exception {
    TestHelper.setupCredentials(rule.jenkins);
    final URL url = new URL(
        rule.getURL() +
            "descriptorByName/io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig/verifyCredentials?" +
            "apiUrl=" + attackerUrl +
            "&imsOrganizationId=imsOrganizationId" +
            "&technicalAccountId=technicalAccountId" +
            "&clientId=clientId" +
            "&clientSecretCredentialsId=" +  CLIENT_SECRET_CRED_ID +
            "&privateKeyCredentialsId=" + PRIVATE_KEY_CRED_ID
    );

    rule.jenkins.setCrumbIssuer(null);
    rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());

    GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
    strategy.add(Jenkins.ADMINISTER, "admin");
    strategy.add(Jenkins.READ, "user");
    rule.jenkins.setAuthorizationStrategy(strategy);

    { // Read Only User
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
      assertEquals(CLIENT_SECRET, attackerServlet.clientSecret);
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

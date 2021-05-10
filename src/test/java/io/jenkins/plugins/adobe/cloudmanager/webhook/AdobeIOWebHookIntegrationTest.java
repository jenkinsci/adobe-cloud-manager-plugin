package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import javax.ws.rs.HttpMethod;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class AdobeIOWebHookIntegrationTest {

  @Rule
  public JenkinsRule rule  = new JenkinsRule();

  @Before
  public void before() throws Exception {
    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
  }

  @Test
  public void testChallenge() throws Exception {

    String challenge = "ChallengeParameter";

    String url = String.format("%s%s/?challenge=%s", rule.getURL().toString(), AdobeIOWebHook.URL_NAME, challenge);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.GET);
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
    con.connect();
    assertEquals(HttpURLConnection.HTTP_OK, con.getResponseCode());
    String challengeResponse = IOUtils.toString(con.getInputStream(), Charset.defaultCharset());
    assertEquals(challenge, challengeResponse);
  }

}

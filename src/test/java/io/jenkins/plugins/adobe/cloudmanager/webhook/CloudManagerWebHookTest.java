package io.jenkins.plugins.adobe.cloudmanager.webhook;

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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber.CloudManagerEventSubscriber;
import io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber.CloudManagerSubscriberEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class CloudManagerWebHookTest {

  @Rule
  public JenkinsRule rule = new JenkinsRule();

  private static String sign(String toSign) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
  }

  @Before
  public void before() throws Exception {
    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
  }

  @Test
  public void testChallengeEvent() throws Exception {

    String challenge = "ChallengeParameter";
    String url = String.format("%s%s/?challenge=%s", rule.getURL().toString(), CloudManagerWebHook.URL_NAME, challenge);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.GET);
    con.setRequestProperty(CloudManagerEvent.SIGNATURE_HEADER, sign(challenge));
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
    con.connect();
    assertEquals(HttpURLConnection.HTTP_OK, con.getResponseCode());
    String challengeResponse = IOUtils.toString(con.getInputStream(), Charset.defaultCharset());
    assertEquals(challenge, challengeResponse);
  }

  @Test
  public void testPipelineExecutionStartEvent() throws Exception {

    String body = IOUtils.resourceToString("events/pipeline-started.json", Charset.defaultCharset(), this.getClass().getClassLoader());
    String url = String.format("%s%s/", rule.getURL().toString(), CloudManagerWebHook.URL_NAME);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.POST);
    con.setRequestProperty(CloudManagerEvent.SIGNATURE_HEADER, sign(body));
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
    con.setDoOutput(true);
    IOUtils.write(body, con.getOutputStream(), Charset.defaultCharset());
    assertEquals(HttpServletResponse.SC_OK, con.getResponseCode());

    PipelineStartEventSubscriber subscriber = rule.jenkins.getExtensionList(PipelineStartEventSubscriber.class).get(0);

    while (subscriber.event == null) {
      Thread.sleep(1000);
    }
    CloudManagerSubscriberEvent expected = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, CloudManagerEvent.EventType.PIPELINE_STARTED, body);
    assertEquals(expected, subscriber.event);
  }

  @Test
  public void testPipelineExecutionStepEvent() throws Exception {

    String body = IOUtils.resourceToString("events/step-started.json", Charset.defaultCharset(), this.getClass().getClassLoader());
    String url = String.format("%s%s/", rule.getURL().toString(), CloudManagerWebHook.URL_NAME);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.POST);
    con.setRequestProperty(CloudManagerEvent.SIGNATURE_HEADER, sign(body));
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
    con.setDoOutput(true);
    IOUtils.write(body, con.getOutputStream(), Charset.defaultCharset());
    assertEquals(HttpServletResponse.SC_OK, con.getResponseCode());

    PipelineStepEventSubscriber subscriber = rule.jenkins.getExtensionList(PipelineStepEventSubscriber.class).get(0);

    while (subscriber.event == null) {
      Thread.sleep(1000);
    }
    CloudManagerSubscriberEvent expected = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, CloudManagerEvent.EventType.STEP_STARTED, body);
    assertEquals(expected, subscriber.event);
  }

  @Test
  public void testPipelineExecutionEndEvent() throws Exception {

    String body = IOUtils.resourceToString("events/pipeline-ended.json", Charset.defaultCharset(), this.getClass().getClassLoader());
    String url = String.format("%s%s/", rule.getURL().toString(), CloudManagerWebHook.URL_NAME);
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod(HttpMethod.POST);
    con.setRequestProperty(CloudManagerEvent.SIGNATURE_HEADER, sign(body));
    con.setRequestProperty("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
    con.setDoOutput(true);
    IOUtils.write(body, con.getOutputStream(), Charset.defaultCharset());
    assertEquals(HttpServletResponse.SC_OK, con.getResponseCode());

    PipelineEndEventSubscriber subscriber = rule.jenkins.getExtensionList(PipelineEndEventSubscriber.class).get(0);

    while (subscriber.event == null) {
      Thread.sleep(1000);
    }
    CloudManagerSubscriberEvent expected = new CloudManagerSubscriberEvent(AIO_PROJECT_NAME, CloudManagerEvent.EventType.PIPELINE_ENDED, body);
    assertEquals(expected, subscriber.event);
  }

  @TestExtension
  public static class PipelineStepEventSubscriber extends TestSubscriber {
    public PipelineStepEventSubscriber() {
      super(CloudManagerEvent.EventType.STEP_STARTED);
    }
  }

  @TestExtension
  public static class PipelineStartEventSubscriber extends TestSubscriber {
    public PipelineStartEventSubscriber() {
      super(CloudManagerEvent.EventType.PIPELINE_STARTED);
    }
  }

  @TestExtension
  public static class PipelineEndEventSubscriber extends TestSubscriber {
    public PipelineEndEventSubscriber() {
      super(CloudManagerEvent.EventType.PIPELINE_ENDED);
    }
  }

  public static class TestSubscriber extends CloudManagerEventSubscriber {

    private final Set<CloudManagerEvent.EventType> type;
    public CloudManagerSubscriberEvent event;

    public TestSubscriber(CloudManagerEvent.EventType type) {
      this.type = Collections.singleton(type);
    }
    @Nonnull
    @Override
    protected Set<CloudManagerEvent.EventType> types() {
      return type;
    }

    @Override
    protected void onEvent(CloudManagerSubscriberEvent event) {
      this.event = event;
    }
  }
}

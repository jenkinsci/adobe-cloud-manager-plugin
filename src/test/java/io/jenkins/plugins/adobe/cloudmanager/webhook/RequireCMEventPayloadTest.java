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

import java.lang.reflect.InvocationTargetException;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerApiException.ErrorType;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;
import static io.jenkins.plugins.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class RequireCMEventPayloadTest {

  private static final String PAYLOAD = "{}";
  private static final CMEvent EVENT = new CMEvent(PIPELINE_ENDED, IMS_ORG_ID, PAYLOAD);
  @Rule
  public JenkinsRule rule = new JenkinsRule();
  @Tested
  private RequireCMEventPayload.Processor processor;
  @Mocked
  private StaplerRequest request;

  @Test
  public void requiresWebhooksEnabled() throws InvocationTargetException {
    assertThrows(InvocationTargetException.class, () -> processor.requiresWebhookEnabled());
    setupAdobeIOConfigs(rule.jenkins);
    processor.requiresWebhookEnabled();
  }

  @Test
  public void failsOnTooFewArgs() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request }));
  }

  @Test
  public void failsOnTooManyArgs() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ null, null, null }));
  }

  @Test
  public void failsOnNullArg() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, null }));
  }

  @Test
  public void failsOnWrongArg() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, new CloudManagerWebHook() }));
  }

  @Test
  public void failsOnMissingType() {
    new Expectations() {{
      request.getMethod();
      result = "POST";
    }};
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, new CMEvent(null, IMS_ORG_ID, PAYLOAD) }));
  }

  @Test
  public void succeedsOnValidArg() throws InvocationTargetException {
    processor.requiresValidPayload(new Object[]{ request, EVENT });
  }

  @Test
  public void failOnNoAIOProjectsConfigured() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(new Object[]{ request, EVENT }));
  }

  @Test
  public void failOnNoMatchingImsOrgs() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(new Object[]{ request, new CMEvent(PIPELINE_ENDED, "Not Found", PAYLOAD) }));
  }

  @Test
  public void failOnNoSignatureHeader() throws Exception {
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = null;
    }};
    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(new Object[]{ request, EVENT }));
  }

  @Test
  public void failOnNoMatchedProjectSignature() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String secret) throws CloudManagerApiException {
        throw new CloudManagerApiException(ErrorType.VALIDATE_EVENT, "Error");
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(new Object[]{ request, EVENT }));
  }

  @Test
  public void failOnValidateSignatureErrors() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String slientSecret) throws CloudManagerApiException {
        return false;
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(new Object[]{ request, EVENT }));
  }

  @Test
  public void validChallengeSignature() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String secret) throws CloudManagerApiException {
        return true;
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    processor.requiresValidSignature(new Object[]{ request, new CMEvent(PIPELINE_ENDED, null, PAYLOAD) });
  }

  @Test
  public void validEventSignature() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String secret) throws CloudManagerApiException {
        return true;
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    processor.requiresValidSignature(new Object[]{ request, EVENT });
  }
}

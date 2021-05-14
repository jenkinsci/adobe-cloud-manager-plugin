package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.lang.reflect.InvocationTargetException;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerApiException.ErrorType;
import io.adobe.cloudmanager.CloudManagerEvent;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import static io.jenkins.plugin.adobe.cloudmanager.test.TestHelper.*;
import static org.junit.Assert.*;

public class RequireAIOPayloadTest {
  private static final String PAYLOAD = "{}";
  @Rule
  public JenkinsRule rule = new JenkinsRule();
  @Tested
  private RequireAIOPayload.Processor processor;
  @Mocked
  private StaplerRequest request;
  @Mocked
  private StaplerResponse response;

  @Test
  public void requiresWebhooksEnabled() throws InvocationTargetException {
    assertThrows(InvocationTargetException.class, () -> processor.requiresWebhookEnabled());
    setupAdobeIOConfigs(rule.jenkins);
    processor.requiresWebhookEnabled();
  }

  @Test
  public void failsOnTooFewArgs() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, response }));
  }

  @Test
  public void failsOnTooManyArgs() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ null, null, null, null }));
  }

  @Test
  public void failsOnNullArg() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, response, null }));
  }

  @Test
  public void failsOnWrongArg() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidPayload(new Object[]{ request, response, new AdobeIOWebHook() }));
  }

  @Test
  public void succeedsOnValidArg() throws InvocationTargetException {
    processor.requiresValidPayload(new Object[]{ request, response, PAYLOAD });
  }

  @Test
  public void failOnNoAIOProjectsConfigured() {
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(request, new Object[]{ request, response, PAYLOAD }));
  }

  @Test
  public void failOnNoSignatureHeader() throws Exception {
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = null;
    }};
    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(request, new Object[]{ request, response, PAYLOAD }));
  }

  @Test
  public void failOnNoMatchedProjectSignature() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String slientSecret) throws CloudManagerApiException {
        throw new CloudManagerApiException(ErrorType.VALIDATE_EVENT, "Error");
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(request, new Object[]{ request, response, PAYLOAD }));
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
    assertThrows(InvocationTargetException.class, () -> processor.requiresValidSignature(request, new Object[]{ request, response, PAYLOAD }));
  }

  @Test
  public void validSignature() throws Exception {

    new MockUp<CloudManagerEvent>() {
      @Mock
      public boolean isValidSignature(String payload, String digest, String slientSecret) throws CloudManagerApiException {
        return true;
      }
    };
    new Expectations() {{
      request.getHeader(CloudManagerEvent.SIGNATURE_HEADER);
      result = "Signed";
    }};

    setupAdobeIOConfigs(rule.jenkins);
    setupCredentials(rule.jenkins);
    processor.requiresValidSignature(request, new Object[]{ request, response, PAYLOAD });
  }
}

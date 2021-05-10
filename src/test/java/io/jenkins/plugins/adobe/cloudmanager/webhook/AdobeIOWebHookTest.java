package io.jenkins.plugins.adobe.cloudmanager.webhook;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Test;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static org.junit.Assert.*;

public class AdobeIOWebHookTest {
  public static final String PAYLOAD = "Paylooad";

  @Mocked
  private StaplerRequest staplerRequest;

  @Mocked
  private StaplerResponse staplerResponse;

  @Mocked
  private HttpResponses httpResponses;

  @Mocked
  private HttpResponse httpResponse;

  @Tested
  private AdobeIOWebHook webhook;

  @Test
  public void testChallenge() {
    new Expectations() {{
      staplerRequest.getMethod();
      result = "GET";
      HttpResponses.text(PAYLOAD);
      result = httpResponse;
    }};
    assertEquals(httpResponse, webhook.doDynamic(staplerRequest, staplerResponse, PAYLOAD));
  }
}

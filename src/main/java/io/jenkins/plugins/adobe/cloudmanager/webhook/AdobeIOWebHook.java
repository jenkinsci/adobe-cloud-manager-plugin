package io.jenkins.plugins.adobe.cloudmanager.webhook;

import javax.annotation.Nonnull;
import javax.ws.rs.HttpMethod;

import org.apache.commons.lang3.StringUtils;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class AdobeIOWebHook implements UnprotectedRootAction {

  public static final String URL_NAME = "aio-cloud-manager-webhook";
  private static final Logger LOGGER = LoggerFactory.getLogger(AdobeIOWebHook.class);

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return URL_NAME;
  }

  /**
   * Process a AIO WebHook Event
   *
   * @param request  request context
   * @param response response context
   * @param payload  processed payload as a string
   * @return HTTP Response
   */
  @RequireAIOPayload
  public HttpResponse doDynamic(StaplerRequest request, StaplerResponse response, @Nonnull @AIOEventPayload String payload) {
    LOGGER.trace(String.format("Payload: %s", payload));
    if (StringUtils.equals(HttpMethod.GET, request.getMethod())) {
      return doGet(payload);
    }
    return null;
  }

  /**
   * Process a Get request - this will only be for a Challenge
   *
   * @param payload the challenge paylaod
   * @return the HTTP Response
   */
  private HttpResponse doGet(String payload) {
    return HttpResponses.text(payload);
  }
}

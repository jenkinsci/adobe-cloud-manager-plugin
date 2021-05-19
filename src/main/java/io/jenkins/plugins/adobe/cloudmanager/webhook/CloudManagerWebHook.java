package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.io.IOException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;

import org.apache.commons.lang3.StringUtils;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber.CloudManagerEventSubscriber;
import io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber.CloudManagerSubscriberEvent;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming Cloud Manager pipeline events from Adobe IO.
 */
@Extension
public class CloudManagerWebHook implements UnprotectedRootAction {

  public static final String URL_NAME = "aio-cloud-manager-webhook";
  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerWebHook.class);

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
   * Process a AIO WebHook Event.
   * <p>
   *   Calls any {@link CloudManagerEventSubscriber} extensions with the payload information.
   *   These calls are performed asynchronously, as we don't want to block the calling request.
   * </p>
   */
  @RequireCMEventPayload
  public HttpResponse doIndex(StaplerRequest request, final @Nonnull @CMEventPayload CMEvent event) {
    LOGGER.trace(String.format("Payload: %s", event.getPayload()));
    if (StringUtils.equals(HttpMethod.GET, request.getMethod())) {
      return doGet(event.getPayload());
    }

    // Do the notifications async - Don't block the Request thread.
    Timer.get().submit(() -> {
      AdobeIOProjectConfig aioProject = AdobeIOConfig.configuration().getProjectConfigs().stream().filter(cfg -> cfg.getImsOrganizationId().equals(event.getImsOrg())).findFirst().orElse(null);
      if (aioProject == null || StringUtils.isBlank(aioProject.getName())) {
        LOGGER.error(Messages.AdobeIOWebHook_error_missingAIOProject(event.getImsOrg()));
        return;
      }
      Jenkins.get().getExtensionList(CloudManagerEventSubscriber.class).stream()
          .filter(CloudManagerEventSubscriber.interested(event.getEventType()))
          .map(CloudManagerEventSubscriber.process(new CloudManagerSubscriberEvent(aioProject.getName(), event.getEventType(), event.getPayload())))
          .collect(Collectors.toList());
    });
    return HttpResponses.ok();
  }

  // Helper for processing the challenge request.
  private HttpResponse doGet(String payload) {
    return HttpResponses.text(payload);
  }

  /**
   * Excludes the WebHook from requiring CSRF tokens for a POST request.
   */
  @Extension
  @SuppressWarnings("unused")
  public static class CrumbExclusion extends hudson.security.csrf.CrumbExclusion {
    @Override
    public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
      String pathInfo = request.getPathInfo();
      if (StringUtils.isEmpty(pathInfo)) {
        return false;
      }
      pathInfo = pathInfo.endsWith("/") ? pathInfo : pathInfo + '/';
      if (!pathInfo.equals(getExclusionPath())) {
        return false;
      }
      chain.doFilter(request, response);
      return true;
    }
    public String getExclusionPath() {
      return "/" + URL_NAME + "/";
    }
  }
}

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
      String aioProjectName = AdobeIOConfig.configuration().getProjectConfigs()
          .stream()
          .filter(cfg -> {
            String orgId = cfg.getImsOrganizationId();
            return orgId != null && orgId.equals(event.getImsOrg());
          })
          .findFirst()
          .map(AdobeIOProjectConfig::getName)
          .orElse(null);
      if (aioProjectName == null || StringUtils.isBlank(aioProjectName)) {
        LOGGER.error(Messages.CloudManagerWebHook_error_missingAIOProject(event.getImsOrg()));
        return;
      }
      Jenkins.get().getExtensionList(CloudManagerEventSubscriber.class).stream()
          .filter(CloudManagerEventSubscriber.interested(event.getEventType()))
          .map(CloudManagerEventSubscriber.process(new CloudManagerSubscriberEvent(aioProjectName, event.getEventType(), event.getPayload())))
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

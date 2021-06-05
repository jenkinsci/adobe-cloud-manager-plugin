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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.ws.rs.HttpMethod;

import org.apache.commons.lang3.StringUtils;

import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import io.jenkins.plugins.adobe.cloudmanager.util.CredentialsUtil;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.Interceptor;
import org.kohsuke.stapler.interceptor.InterceptorAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.*;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * Mark the WebHook Stapler request method for specific checks, so the WebHook can focus on operations, not validation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@InterceptorAnnotation(RequireCMEventPayload.Processor.class)
public @interface RequireCMEventPayload {

  /**
   * Performs the validation required of an incoming WebHook request before the WebHook is called.
   */
  class Processor extends Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class);

    // Helper for generating a standard response to caller.
    private static void isTrue(boolean condition, String message) throws InvocationTargetException {
      if (!condition) {
        throw new InvocationTargetException(HttpResponses.error(SC_BAD_REQUEST, message));
      }
    }

    /**
     * Runs the validation logic - any issues will raise an exception back to the caller.
     */
    @Override
    public Object invoke(StaplerRequest request, StaplerResponse response, Object instance, Object[] arguments) throws IllegalAccessException, InvocationTargetException, ServletException {
      requiresWebhookEnabled();
      requiresValidPayload(arguments);
      requiresValidSignature(arguments);

      return target.invoke(request, response, instance, arguments);
    }

    /**
     * Precheck to ensure that the webhook is enabled.
     */
    protected void requiresWebhookEnabled() throws InvocationTargetException {
      AdobeIOConfig config = AdobeIOConfig.all().get(AdobeIOConfig.class);
      if (config == null || !config.isWebhookEnabled()) {
        LOGGER.warn(Messages.RequireCMEventPayload_Processor_warn_webhookDisabled());
        throw new InvocationTargetException(HttpResponses.error(SC_NOT_FOUND, "Not Found"));
      }
    }

    /**
     * Precheck that the arguments contain a parsable payload.
     */
    protected void requiresValidPayload(@Nonnull Object[] args) throws InvocationTargetException {
      isTrue(args.length == 2, Messages.RequireCMEventPayload_Processor_error_invalidArgs());
      isTrue(args[1] instanceof CMEvent, Messages.RequireCMEventPayload_Processor_error_invalidArgs());

      CMEvent event = (CMEvent) args[1];
      isTrue(event != null, Messages.RequireCMEventPayload_Processor_error_missingBody());
      StaplerRequest request = (StaplerRequest) args[0];
      if (HttpMethod.POST.equals(request.getMethod())) {
        isTrue(event.getEventType() != null, Messages.RequireCMEventPayload_Processor_error_missingBody());
      }
    }

    /**
     * Precheck to ensure that the request contains a valid signature and that the payload matches.
     */
    protected void requiresValidSignature(Object[] args) throws InvocationTargetException {
      StaplerRequest request = (StaplerRequest) args[0];
      Optional<String> header = Optional.ofNullable(request.getHeader(SIGNATURE_HEADER));
      isTrue(header.isPresent(), Messages.RequireCMEventPayload_Processor_error_missingSignature());

      final CMEvent event = (CMEvent) args[1];
      List<Secret> secrets = AdobeIOConfig.configuration().getProjectConfigs().stream()
          // Challenge requests will have a blank IMS Org
          .filter(cfg -> StringUtils.isBlank(event.getImsOrg()) || StringUtils.equals(cfg.getImsOrganizationId(), event.getImsOrg()))
          .map(AdobeIOProjectConfig::getClientSecretCredentialsId)
          .map(CredentialsUtil::clientSecretFor)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
      isTrue(!secrets.isEmpty(), Messages.RequireCMEventPayload_Processor_error_missingAIOProject());

      final String digest = header.get();
      isTrue(
          secrets.stream().anyMatch(s -> {
            try {
              return CloudManagerEvent.isValidSignature(event.getPayload(), digest, s.getPlainText());
            } catch (CloudManagerApiException e) {
              LOGGER.warn(Messages.RequireCMEventPayload_Processor_warn_signatureValidationError(e.getLocalizedMessage()));
              return false;
            }
          }),
          Messages.RequireCMEventPayload_Processor_error_missingSignature()
      );
    }
  }

}

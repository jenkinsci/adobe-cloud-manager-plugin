package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HttpMethod;

import org.apache.commons.lang3.StringUtils;

import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerEvents;
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
import static io.adobe.cloudmanager.CloudManagerEvents.*;
import static javax.servlet.http.HttpServletResponse.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@InterceptorAnnotation(RequireAIOPayload.Processor.class)
public @interface RequireAIOPayload {

  class Processor extends Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class);

    private static void isTrue(boolean condition, String message) throws InvocationTargetException {
      if (!condition) {
        throw new InvocationTargetException(HttpResponses.errorWithoutStack(SC_BAD_REQUEST, message));
      }
    }

    @Override
    public Object invoke(StaplerRequest request, StaplerResponse response, Object instance, Object[] arguments) throws IllegalAccessException, InvocationTargetException, ServletException {
      requiresWebhookEnabled();
      requiresValidPayload(arguments);
      if (HttpMethod.POST.equals(request.getMethod())) {
        requiresValidSignature(request, arguments);
      }

      return target.invoke(request, response, instance, arguments);
    }

    /**
     * Precheck to ensure that the webhook is enabled.
     *
     * @throws InvocationTargetException if webhook is not enabled
     */
    protected void requiresWebhookEnabled() throws InvocationTargetException {
      AdobeIOConfig config = AdobeIOConfig.all().get(AdobeIOConfig.class);
      if (config == null || !config.isWebhookEnabled()) {
        throw new InvocationTargetException(HttpResponses.error(SC_NOT_FOUND, Messages.RequireAIOPayload_Processor_warn_webhookDisabled()));
      }
    }

    /**
     * Precheck that the arguments contain a parsable payload.
     *
     * @param args payload, not null and not blank.
     * @throws InvocationTargetException if conditions are not met
     */
    protected void requiresValidPayload(@Nonnull Object[] args) throws InvocationTargetException {
      isTrue(args.length == 3, Messages.RequireAIOPayload_Processor_error_invalidArgs());

      isTrue(StringUtils.isNotBlank((String) Arrays.stream(args).filter(a -> a instanceof String).findFirst().orElse("")),
          Messages.RequireAIOPayload_Processor_error_missingBody());
    }

    /**
     * Precheck to ensure that the request contains a valid signature and that the payload matches.
     *
     * @param request stapler request
     * @param arguments event payload
     * @throws InvocationTargetException if conditions are not met
     */
    protected void requiresValidSignature(StaplerRequest request, Object[] arguments) throws InvocationTargetException {
      List<Secret> secrets = AdobeIOConfig.configuration().getProjectConfigs().stream()
          .map(AdobeIOProjectConfig::getClientSecretCredentialsId)
          .map(CredentialsUtil::clientSecretFor)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
      isTrue(!secrets.isEmpty(), Messages.RequireAIOPayload_Processor_error_missingAIOProject());

      Optional<String> header = Optional.ofNullable(request.getHeader(SIGNATURE_HEADER));
      isTrue(header.isPresent(), Messages.RequireAIOPayload_Processor_error_missingSignature());

      final String payload = (String) arguments[2];
      String digest = header.get();
      isTrue(
          secrets.stream().anyMatch(s -> {
            try {
              return CloudManagerEvents.isValidSignature(payload, digest, s.getPlainText());
            } catch (CloudManagerApiException e) {
              LOGGER.warn(Messages.RequireAIOPayload_Processor_warn_signatureValidationError(e.getLocalizedMessage()));
              return false;
            }
          }),
          Messages.RequireAIOPayload_Processor_error_missingSignature()
      );
    }
  }

}

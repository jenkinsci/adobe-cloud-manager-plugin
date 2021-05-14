package io.jenkins.plugins.adobe.cloudmanager.webhook;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerEvent;
import org.kohsuke.stapler.AnnotationHandler;
import org.kohsuke.stapler.InjectedParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Documented
@InjectedParameter(AIOEventPayload.PayloadHandler.class)
public @interface AIOEventPayload {
  class PayloadHandler extends AnnotationHandler<AIOEventPayload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIOEventPayload.PayloadHandler.class);

    public static final String CHALLENGE_PARAM = "challenge";

    private static final Map<String, Function<StaplerRequest, String>> PROCESSORS;
    static {
      Map<String, Function<StaplerRequest, String>> procs = new HashMap<>();
      procs.put(null, fromParam()); // For Testing
      procs.put(ContentType.APPLICATION_FORM_URLENCODED.getMimeType(), fromParam());
      procs.put(ContentType.APPLICATION_JSON.getMimeType(), fromBody());
      PROCESSORS = Collections.unmodifiableMap(procs);
    }

    @Override
    public Object parse(StaplerRequest request, AIOEventPayload AIOEventPayload, Class clazz, String paramName) throws ServletException {

      String contentType = request.getContentType();

      if (!PROCESSORS.containsKey(contentType)) {
        LOGGER.error(Messages.AIOEventPayload_PayloadHandler_error_unknownContentType(contentType));
        return null;
      }
      String payload = PROCESSORS.get(contentType).apply(request);
      LOGGER.trace(Messages.AIOEventPayload_PayloadHandler_trace_payload(payload));
      return payload;
    }

    protected static Function<StaplerRequest, String> fromParam() {
      return (request) -> request.getParameter(CHALLENGE_PARAM);
    }

    protected static Function<StaplerRequest, String> fromBody() {
      return (request) -> {
        try {
          String body = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
          CloudManagerEvent.parseEvent(body, CloudManagerEvent.Type.from(body).getClass());
          return body;
        } catch (IOException e) {
          LOGGER.error(Messages.AIOEventPayload_PayloadHandler_error_io(e.getLocalizedMessage()));
        } catch (IllegalArgumentException e) {
          LOGGER.error(Messages.AIOEventPayload_PayloadHandler_error_unknownEventType(e.getLocalizedMessage()));
        } catch (CloudManagerApiException e) {
          LOGGER.error(Messages.AIOEventPayload_PayloadHandler_error_eventParse(e.getLocalizedMessage()));
        }
        return null;
      };
    }
  }
}

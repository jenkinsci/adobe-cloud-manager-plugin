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
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.CloudManagerEvent.EventType;
import io.adobe.cloudmanager.event.PipelineExecutionEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepWaitingEvent;
import org.kohsuke.stapler.AnnotationHandler;
import org.kohsuke.stapler.InjectedParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Documented
@InjectedParameter(CMEventPayload.PayloadHandler.class)
public @interface CMEventPayload {
  class PayloadHandler extends AnnotationHandler<CMEventPayload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CMEventPayload.PayloadHandler.class);

    public static final String CHALLENGE_PARAM = "challenge";

    private static final Map<String, Function<StaplerRequest, CMEvent>> PROCESSORS;
    static {
      Map<String, Function<StaplerRequest, CMEvent>> procs = new HashMap<>();
      procs.put(null, fromParam()); // For Testing
      procs.put(ContentType.APPLICATION_FORM_URLENCODED.getMimeType(), fromParam());
      procs.put(ContentType.APPLICATION_JSON.getMimeType(), fromBody());
      PROCESSORS = Collections.unmodifiableMap(procs);
    }

    @Override
    public Object parse(StaplerRequest request, CMEventPayload cmEventPayload, Class clazz, String paramName) throws ServletException {

      String contentType = request.getContentType();

      if (!PROCESSORS.containsKey(contentType)) {
        LOGGER.warn(Messages.CMEventPayload_PayloadHandler_warn_unknownContentType(contentType));
        return null;
      }
      CMEvent event = PROCESSORS.get(contentType).apply(request);
      if (event != null) {
        LOGGER.trace(Messages.CMEventPayload_PayloadHandler_trace_payload(event.getPayload()));
      }
      return event;
    }

    protected static Function<StaplerRequest, CMEvent> fromParam() {
      return (request) -> {
        if (request.getParameter(CHALLENGE_PARAM) == null) {
          LOGGER.error(Messages.CMEventPayload_PayloadHandler_warn_missingChallengeParameter());
          return null;
        }
        return new CMEvent(null, null, request.getParameter(CHALLENGE_PARAM));
      };
    }

    protected static Function<StaplerRequest, CMEvent> fromBody() {
      return (request) -> {
        try {
          String body = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);

          EventType type = EventType.from(body);
          String imsOrg = null;
          try {
            switch (type) {
              case PIPELINE_STARTED:
                imsOrg = CloudManagerEvent.parseEvent(body, PipelineExecutionStartEvent.class).getEvent().getActivitystreamsto().getXdmImsOrgid();
                break;
              case PIPELINE_ENDED:
                imsOrg = CloudManagerEvent.parseEvent(body, PipelineExecutionEndEvent.class).getEvent().getActivitystreamsto().getXdmImsOrgid();
                break;
              case STEP_STARTED:
                imsOrg = CloudManagerEvent.parseEvent(body, PipelineExecutionStepStartEvent.class).getEvent().getActivitystreamsto().getXdmImsOrgid();
                break;
              case STEP_WAITING:
                imsOrg = CloudManagerEvent.parseEvent(body, PipelineExecutionStepWaitingEvent.class).getEvent().getActivitystreamsto().getXdmImsOrgid();
                break;
              case STEP_ENDED:
                imsOrg = CloudManagerEvent.parseEvent(body, PipelineExecutionStepEndEvent.class).getEvent().getActivitystreamsto().getXdmImsOrgid();
                break;
            }
          } catch (NullPointerException e) {
            // Protect against poorly formatted JSON?
            LOGGER.warn(Messages.CMEventPayload_PayloadHandler_warn_eventParse(body));
            return null;
          }
          return new CMEvent(type, imsOrg, body);
        } catch (IOException e) {
          LOGGER.warn(Messages.CMEventPayload_PayloadHandler_warn_io(e.getLocalizedMessage()));
        } catch (CloudManagerApiException e) {
          LOGGER.warn(Messages.CMEventPayload_PayloadHandler_warn_eventParse(e.getLocalizedMessage()));
        }
        return null;
      };
    }
  }
}

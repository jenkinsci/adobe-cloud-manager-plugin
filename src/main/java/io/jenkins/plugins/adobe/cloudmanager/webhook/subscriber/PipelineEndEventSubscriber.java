package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;

import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionEndEvent;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineEndExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;

public class PipelineEndEventSubscriber extends CloudManagerEventSubscriber {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEndEventSubscriber.class);
  private static final Set<CloudManagerEvent.EventType> EVENTS = Collections.singleton(PIPELINE_ENDED);

  @Nonnull
  @Override
  protected Set<CloudManagerEvent.EventType> types() {
    return EVENTS;
  }

  @Override
  protected void onEvent(CloudManagerSubscriberEvent event) {
    CloudManagerApi api = createApi(event.getAioProjectName());
    if (api == null) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_createApi());
      return;
    }
    try {
      final PipelineExecution pe = api.getExecution(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionEndEvent.class));
      StepExecution.applyAll(PipelineEndExecution.class, (execution) -> {
        if (execution.isApplicable(pe)) {
          try {
            execution.occurred(pe);
          } catch (IOException | InterruptedException ex) {
            LOGGER.error(Messages.CloudManagerEventSubscriber_error_notifyExecution(ex.getLocalizedMessage()));
          }
        }
        return null;
      });
    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_api(e.getLocalizedMessage()));
    }
  }
}

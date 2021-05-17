package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import hudson.Extension;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.PipelineExecution;
import io.adobe.cloudmanager.PipelineExecutionStepState;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepEndEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepStartEvent;
import io.adobe.cloudmanager.event.PipelineExecutionStepWaitingEvent;
import io.jenkins.plugins.adobe.cloudmanager.step.execution.PipelineStepStateExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.adobe.cloudmanager.event.CloudManagerEvent.EventType.*;

@Extension
public class PipelineStepEventSubscriber extends CloudManagerEventSubscriber {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStepEventSubscriber.class);

  private static final Set<CloudManagerEvent.EventType> EVENTS = new HashSet<>(Arrays.asList(STEP_STARTED, STEP_WAITING, STEP_ENDED));

  @Nonnull
  @Override
  protected Set<CloudManagerEvent.EventType> types() {
    return EVENTS;
  }

  @Override
  protected void onEvent(final CloudManagerSubscriberEvent event) {
    CloudManagerApi api = createApi(event.getAioProjectName());
    if (api == null) {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_createApi());
      return;
    }
    try {

      final PipelineExecutionStepState stepState;
      switch (event.getType()) {
        case STEP_STARTED:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepStartEvent.class));
          break;
        case STEP_WAITING:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepWaitingEvent.class));
          break;
        case STEP_ENDED:
          stepState = api.getExecutionStepState(CloudManagerEvent.parseEvent(event.getPayload(), PipelineExecutionStepEndEvent.class));
          break;
        default:
          LOGGER.warn(Messages.PipelineStepEventSubscriber_warn_invalidStepState(event.getType()));
          return;
      }

      final PipelineExecution pipelineExecution = stepState.getExecution();
      StepExecution.applyAll(PipelineStepStateExecution.class, (execution) -> {
        if (execution.isApplicable(stepState) && execution.isApplicable(pipelineExecution)) {
          try {
            if (event.getType() == STEP_STARTED || event.getType() == STEP_ENDED) {
              execution.occurred(pipelineExecution, stepState);
            } else {
              execution.waiting(pipelineExecution, stepState);
            }
          } catch (IOException | InterruptedException | TimeoutException ex) {
            LOGGER.error(Messages.PipelineStepEventSubscriber_error_notifyExecution(ex.getLocalizedMessage()));
          }
        }
        return null;
      });
    } catch (CloudManagerApiException e) {
      LOGGER.error(Messages.PipelineStepEventSubscriber_error_api(e.getLocalizedMessage()));
    }
  }
}

package io.jenkins.plugins.adobe.cloudmanager.webhook.subscriber;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.Secret;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.event.CloudManagerEvent;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOConfig;
import io.jenkins.plugins.adobe.cloudmanager.config.AdobeIOProjectConfig;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CloudManagerEventSubscriber implements ExtensionPoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerEventSubscriber.class);

  /**
   * Lists of all Cloud Manager Event Subscribers
   */
  public static ExtensionList<CloudManagerEventSubscriber> all() {
    return Jenkins.get().getExtensionList(CloudManagerEventSubscriber.class);
  }

  /**
   * Predicate for filtering on streams.
   */
  public static Predicate<CloudManagerEventSubscriber> interested(final CloudManagerEvent.EventType type) {
    return (subscriber) -> subscriber.types().contains(type);
  }


  /**
   * Function for processing events via streams.
   */
  public static Function<CloudManagerEventSubscriber, Void> process(final CloudManagerSubscriberEvent event) {
    return (subscriber) -> {
      subscriber.onEvent(event);
      return null;
    };
  }

  @CheckForNull
  protected CloudManagerApi createApi(String projectName) {
    AdobeIOProjectConfig aioProject = AdobeIOConfig.projectConfigFor(projectName);
    if (aioProject != null) {
      Secret token = aioProject.authenticate();
      if (token != null) {
        return CloudManagerApi.create(aioProject.getImsOrganizationId(), aioProject.getClientId(), token.getPlainText(), aioProject.getApiUrl());
      }
    } else {
      LOGGER.error(Messages.CloudManagerEventSubscriber_error_missingAioProject(projectName));
    }
    return null;
  }

  /**
   * List of event types that this subscriber can process.
   */
  @Nonnull
  protected abstract Set<CloudManagerEvent.EventType> types();

  /**
   * Processes the event payload.
   */
  protected abstract void onEvent(final CloudManagerSubscriberEvent event);

}

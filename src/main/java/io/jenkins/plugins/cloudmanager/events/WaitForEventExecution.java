package io.jenkins.plugins.cloudmanager.events;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class WaitForEventExecution extends AbstractStepExecutionImpl implements EventSubscriber {

    /**
     * 
     */
    private static final long serialVersionUID = 2623012074853811112L;
    private final EventSelector eventSelector;
    private final SecretKeySpec hmacKey;
    
    public WaitForEventExecution(StepContext context, EventSelector eventSelector, String clientSecret) {
        super(context);
        this.eventSelector = eventSelector;
        hmacKey = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.US_ASCII), WebhookRootAction.HMAC_SHA256);
    }

    @Override
    public boolean start() throws Exception {
        WebhookRootAction.subscribe(eventSelector, this);
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        WebhookRootAction.unsubscribe(eventSelector, this);
        getContext().onFailure(cause);
    }

    @Override
    public void onEvent(Event event) {
        getContext().onSuccess(event);
    }

    @Override
    public SecretKeySpec getHmacKey() {
        return hmacKey;
    }

}

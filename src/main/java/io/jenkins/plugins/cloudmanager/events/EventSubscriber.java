package io.jenkins.plugins.cloudmanager.events;

import javax.crypto.spec.SecretKeySpec;

public interface EventSubscriber {

    void onEvent(Event event);
    
    SecretKeySpec getHmacKey();
}

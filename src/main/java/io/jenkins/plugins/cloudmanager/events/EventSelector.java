package io.jenkins.plugins.cloudmanager.events;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EventSelector implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 323392547220880889L;
    private final String organizationId;
    private final Set<String> types;
    private final Set<String> objectTypes;

    // TODO: support execution id
    public EventSelector(String organizationId, Collection<String> types, Collection<String> objectTypes) {
        if ((types == null || types.isEmpty()) && (objectTypes == null || objectTypes.isEmpty())) {
            throw new IllegalArgumentException("Either types or objectTypes have to be not empty to match to an event");
        }
        if (types != null) {
            this.types = new HashSet<>(types);
        } else {
            this.types = Collections.emptySet();
        }
        if (objectTypes != null) {
            this.objectTypes = new HashSet<>(objectTypes);
        } else {
            this.objectTypes = Collections.emptySet();
        }
        this.organizationId = organizationId;
    }

    boolean matches(Event event) {
        if (!organizationId.equals(event.getOrganizationId())) {
            return false;
        }
        if (!types.isEmpty() && !types.contains(event.getType())) {
            return false;
        }
        if (!objectTypes.isEmpty() && !objectTypes.contains(event.getObjectType())) {
            return false;
        }
        return true;
    }
}

package io.jenkins.plugins.cloudmanager.events;

import java.io.Serializable;
import java.util.Map;

import net.sf.json.JSONObject;

/**
 * Encapsulating an Adobe IO Event
 * @see <a href="https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/receiving-events.md">Cloud Manager Events</a>
 */
public class Event implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -747647422846065702L;
    public static final String TYPE_CLOUDMANAGER_ENDED = "https://ns.adobe.com/experience/cloudmanager/event/ended";
    public static final String OBJECT_TYPE_CLOUDMANAGER_PIPELINE_EXECUTION_STEP = "https://ns.adobe.com/experience/cloudmanager/execution-step-state";
    private final String id;
    private final String type;
    private final String objectType;
    private final String organizationId;
    private final Map<String,Object> object;

    public Event(String id, String type, String objectType, Map<String,Object> object, String organizationId) {
        super();
        this.id = id;
        this.type = type;
        this.objectType = objectType;
        this.object = object;
        this.organizationId = organizationId;
    }

    static Event parse(JSONObject jsonPayload) {
        // extract type and object from event payload object (https://www.adobe.io/apis/experiencecloud/cloud-manager/api-reference.html#!AdobeDocs/cloudmanager-api-docs/master/swagger-specs/events.yaml)
        JSONObject event = jsonPayload.getJSONObject("event");
        // documentation slightly wrong: https://github.com/AdobeDocs/cloudmanager-api-docs/issues/31
        return new Event(event.getString("@id"), event.getString("@type"), event.getString("xdmEventEnvelope:objectType"), event.getJSONObject("activitystreams:object"), event.getJSONObject("activitystreams:to").getString("xdmImsOrg:id"));
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getObjectType() {
        return objectType;
    }

    public Map<String,Object> getObject() {
        return object;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    @Override
    public String toString() {
        return "Event [id=" + id + ", type=" + type + ", objectType=" + objectType + ", organizationId=" + organizationId + ", object="
                + object + "]";
    }

    
}

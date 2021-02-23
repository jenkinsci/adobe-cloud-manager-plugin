package io.jenkins.plugins.cloudmanager.events;

import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedReader;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import net.sf.json.JSONObject;

/**
 * Inspired by https://github.com/jenkinsci/webhook-step-plugin/blob/master/src/main/java/org/jenkinsci/plugins/webhookstep/WebhookRootAction.java/
 * But tailored for Adobe IO triggered webhooks
 * 
 * @see <a herf="https://www.adobe.io/apis/experienceplatform/events/docs.html#!adobedocs/adobeio-events/master/intro/webhooks_intro.md">Adobe IO Webhooks</a>
 */
@Extension
public class WebhookRootAction extends CrumbExclusion implements UnprotectedRootAction {

    public static final String HMAC_SHA256 = "HmacSHA256";
    private static final String URL_PREFIX = "adobe-cloudmanager-aio-events-webhook";
    private static final String PARAMETER_CHALLENGE = "challenge";
    
    private static final Logger LOGGER = Logger.getLogger(WebhookRootAction.class.getName());
    private static final String HEADER_ADOBE_SIGNATURE = "x-adobe-signature";
    
    private static Map<EventSelector, EventSubscriber> eventSubscribers = new HashMap<>();

    @Override
    public String getDisplayName() {
        return null; // no UI
    }

    @Override
    public String getIconFileName() {
        return null; // no UI
    }

    @Override
    public String getUrlName() {
        return URL_PREFIX;
    }

    /**
     * Allows anonymous POST requests without CSRF tokens for the URL_PREFIX
     */
    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/"+ URL_PREFIX + "/")) {
            chain.doFilter(req, resp);
            return true;
        }
        return false;
    }

    /**
     * Request handling (both POST and GET) triggered from Adobe IO
     */
    @POST
    @GET
    public void doDynamic(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        if ("GET".equals(request.getMethod())) {
            respondToChallenge(request, response);
        } else {
            try {
                notifyListeners(request, response);
            } catch (NoSuchAlgorithmException|InvalidKeyException e) {
                LOGGER.log(Level.INFO, "Error processing webhook: " + e.getMessage(), e);
            } 
        }
    }

    private void notifyListeners(StaplerRequest request, StaplerResponse response) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        // only read up to 5k of payload
        try (Reader reader = new BoundedReader(request.getReader(), 5000)) {
            // need both raw and as string (for JSON parsing)
            String payload = IOUtils.toString(reader);
            LOGGER.log(Level.INFO, "Received POST webhook: " + payload);
            Event event = Event.parse(JSONObject.fromObject(payload));
            synchronized(eventSubscribers) {
                for (Map.Entry<EventSelector, EventSubscriber> entry : eventSubscribers.entrySet()) {
                    if (entry.getKey().matches(event)) {
                        if (validateSignature(entry.getValue().getHmacKey(), request.getHeader(HEADER_ADOBE_SIGNATURE), payload, request.getCharacterEncoding())) {
                            LOGGER.log(Level.INFO, "Notify subscriber: " + entry.getValue() + " about event " + event);
                            entry.getValue().onEvent(event);
                        } else {
                            LOGGER.log(Level.WARNING, "Disregard request due to invalid signature");
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * https://www.adobe.io/apis/experienceplatform/events/docs.html#authenticate-events
     * @throws NoSuchAlgorithmException 
     * @throws InvalidKeyException 
     * @throws UnsupportedEncodingException 
     * @throws IllegalStateException 
     */
    private boolean validateSignature(SecretKeySpec secretKeySpec, String signature, String payload, String characterEncoding) throws NoSuchAlgorithmException, InvalidKeyException, IllegalStateException, UnsupportedEncodingException {
        if (StringUtils.isEmpty(signature)) {
            LOGGER.log(Level.WARNING, "Received request with missing signature");
            return false;
        }
        // calculate hmacSHA256 over raw body
        Mac hmacSha256 = Mac.getInstance(HMAC_SHA256);
        hmacSha256.init(secretKeySpec);
        String expectedSignature = Base64.getEncoder().encodeToString(hmacSha256.doFinal(payload.getBytes(characterEncoding)));
        return signature.equals(expectedSignature);
    }

    /**
     * Responds to <a href="https://www.adobe.io/apis/experienceplatform/events/docs.html#!adobedocs/adobeio-events/master/intro/webhooks_intro.md#the-challenge-request">challenge requests</a>.
     * @param request
     * @param response
     * @throws IOException 
     */
    private void respondToChallenge(StaplerRequest request, StaplerResponse response) throws IOException {
        String challenge = request.getParameter(PARAMETER_CHALLENGE);
        if (!StringUtils.isEmpty(challenge)) {
            response.setContentType("text/plain");
            response.getWriter().write(challenge);
        } else {
            LOGGER.log(Level.WARNING, "Received get request without challenge parameter");
        }
    }

    public static void subscribe(EventSelector eventSelector, EventSubscriber subscriber) {
        synchronized (eventSubscribers) {
           eventSubscribers.put(eventSelector, subscriber);
        }
    }

    public static boolean unsubscribe(EventSelector eventSelector, EventSubscriber subscriber) {
        synchronized (eventSubscribers) {
            return eventSubscribers.remove(eventSelector, subscriber);
        }
    }
}

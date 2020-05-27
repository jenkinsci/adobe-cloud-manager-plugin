package io.jenkins.plugins.cloudmanager;

import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CACHE_CONTRL;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CLIENT_ID;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CLIENT_SECRET;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CLOUD_MANAGER_JWT_SCOPE;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CONTENT_TYPE;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.CONTENT_TYPE_URL_ENCODED;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.IMS_ENDPOINT;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.IMS_JWT_EXCHANGE_ENDPOINT;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.JSON_ACCESS_TOKEN;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.JWT_TOKEN;
import static io.jenkins.plugins.cloudmanager.AdobeioConstants.NO_CACHE;
import static io.jsonwebtoken.SignatureAlgorithm.RS256;

import hudson.util.Secret;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// an adaptation of:
// https://github.com/Adobe-Consulting-Services/acs-aem-commons/blob/59b165575017da39b61a317d89bb303571149bbc/bundle/src/main/java/com/adobe/acs/commons/adobeio/service/impl/IntegrationServiceImpl.java
public class CloudManagerAuthUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudManagerAuthUtil.class);
  private static final Base64.Decoder DECODER = Base64.getMimeDecoder();

  /**
   * Get access token
   *
   * @return
   */
  public static String getAccessToken(AdobeioConfig config) throws AdobeIOException{
    String token = null;
    HttpResponse<JsonNode> response =
        Unirest.post(IMS_JWT_EXCHANGE_ENDPOINT)
            .header(CACHE_CONTRL, NO_CACHE)
            .header(CONTENT_TYPE, CONTENT_TYPE_URL_ENCODED)
            .field(CLIENT_ID, safeGetPlainText(config.getApiKey()))
            .field(CLIENT_SECRET, safeGetPlainText(config.getClientSecret()))
            .field(JWT_TOKEN, getJwtToken(config))
            .asJson();



    if (response.isSuccess()) {
      JSONObject responseObject = response.getBody().getObject();
      if (responseObject.has(JSON_ACCESS_TOKEN)) {
        token = responseObject.getString(JSON_ACCESS_TOKEN);
      } else {
        throw new AdobeIOException("JWT Exchange response does not contain an access token.");
      }
    } else {
      LOGGER.info("Failed with response code {} ", response.getStatus());
      LOGGER.info("Failed with response body {} ", response.getBody());
      throw new AdobeIOException("Failed to get access token, Adobe send response: "
          + "[" + response.getStatus() + "] "
          + response.getBody());
    }
    return token;
  }

  public static String safeGetPlainText(Secret secret) {
    return Optional.ofNullable(secret).map(Secret::getPlainText).orElse(null);
  }

  private static String getJwtToken(AdobeioConfig config) {
    String jwtToken;
    try {
      jwtToken =
          Jwts.builder()
              // claims
              .setIssuer(config.getOrganizationID())
              .setSubject(config.getTechnicalAccountId())
              .setExpiration(getExpirationDate())
              .setAudience(String.format("%s/c/%s", IMS_ENDPOINT, safeGetPlainText(config.getApiKey())))
              .claim(CLOUD_MANAGER_JWT_SCOPE, Boolean.TRUE)
              // sign
              .signWith(RS256, getPrivateKey(config))
              .compact();
    } catch (Exception e) { // yeah yeah, rethrow them all.
      throw new IllegalStateException("Error while generating JWT token", e);
    }
    return jwtToken;
  }

  private static PrivateKey getPrivateKey(AdobeioConfig config)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] decodedPrivateKey =
        Optional.ofNullable(safeGetPlainText(config.getPrivateKey()))
            // Remove the "BEGIN" and "END" lines, as well as any whitespace
            .map(k -> k.replaceAll("-----\\w+ PRIVATE KEY-----", ""))
            .map(k -> k.replaceAll("\\s+", ""))
            .map(DECODER::decode)
            .orElse(null);
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedPrivateKey);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePrivate(keySpec);
  }

  private static Date getExpirationDate() {
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.HOUR, 24);
    return cal.getTime();
  }
}

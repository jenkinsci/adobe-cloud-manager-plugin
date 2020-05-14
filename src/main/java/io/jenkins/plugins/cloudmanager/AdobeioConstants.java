package io.jenkins.plugins.cloudmanager;

// copy of:
// https://github.com/Adobe-Consulting-Services/acs-aem-commons/blob/59b165575017da39b61a317d89bb303571149bbc/bundle/src/main/java/com/adobe/acs/commons/adobeio/service/impl/AdobeioConstants.java
public final class AdobeioConstants {
  public static final String IMS_ENDPOINT = "https://ims-na1.adobelogin.com";
  public static final String IMS_JWT_EXCHANGE_ENDPOINT = IMS_ENDPOINT + "/ims/exchange/jwt/";
  public static final String CLOUD_MANAGER_JWT_SCOPE =
      "https://ims-na1.adobelogin.com/s/ent_cloudmgr_sdk";
  public static final String CLOUD_MANAGER_BASE_PATH = "https://cloudmanager.adobe.io";

  public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

  public static final String CONTENT_TYPE_URL_ENCODED = "application/x-www-form-urlencoded";

  public static final String X_API_KEY = "X-Api-Key";

  public static final String CLIENT_ID = "client_id";

  public static final String CLIENT_SECRET = "client_secret";

  public static final String JWT_TOKEN = "jwt_token";

  public static final String AUTHORIZATION = "authorization";

  public static final String BEARER = "Bearer ";

  public static final String CACHE_CONTRL = "cache-control";

  public static final String NO_CACHE = "no-cache";

  public static final String CONTENT_TYPE = "content-type";

  public static final String JSON_ACCESS_TOKEN = "access_token";
  public static final String JK_PKEY = "PKey";
  public static final String JK_SUBSCRIBER = "subscriber";

  public static final String RESULT_ERROR = "error";
  public static final String RESULT_NO_DATA = "nodata";

  private AdobeioConstants() {}
}

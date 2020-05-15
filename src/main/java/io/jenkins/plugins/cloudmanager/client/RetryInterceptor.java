package io.jenkins.plugins.cloudmanager.client;

import hudson.ExtensionList;
import io.jenkins.plugins.cloudmanager.AdobeioConstants;
import io.jenkins.plugins.cloudmanager.CloudManagerGlobalConfig;
import java.io.IOException;
import java.util.logging.Logger;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

/** Retry requests returning 401. Will try to get a new access token and retry. **/
public class RetryInterceptor implements Interceptor {

  private static final Logger logger = Logger.getLogger(RetryInterceptor.class.getName());

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Response response = chain.proceed(request);
    if (!response.isSuccessful() && 401 == response.code()) {
      response.close(); // close old response
      logger.info(
          "Request to "
              + request.url().toString()
              + "was not successful with given access token "
              + "Attempting to get a new token and retry. ");
      CloudManagerGlobalConfig config =
          ExtensionList.lookupSingleton(CloudManagerGlobalConfig.class);
      String newAccessToken = config.getAccessToken();
      if (StringUtils.isNoneBlank(newAccessToken)) {
        logger.info("Got a new token! Retrying request.");
      } else {
        logger.info(
            "Could not get new token. Will still retry the request which will likely fail.");
      }
      Request retryRequest =
          request
              .newBuilder()
              .header(AdobeioConstants.AUTHORIZATION, AdobeioConstants.BEARER + newAccessToken)
              .build();
      response = chain.proceed(retryRequest);
    }
    return response;
  }
}

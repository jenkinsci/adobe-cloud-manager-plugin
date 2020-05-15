package io.jenkins.plugins.cloudmanager.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.jenkins.plugins.cloudmanager.AdobeioConfig;
import io.jenkins.plugins.cloudmanager.AdobeioConstants;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeFormatterBuilder;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public abstract class AbstractService<T> {

  protected String organizationId, authorization, apiKey;
  protected T api;

  public AbstractService(AdobeioConfig config, Class<T> apiClazz) {
    this.organizationId = config.getOrganizationID();
    this.authorization = AdobeioConstants.BEARER + config.getAccessToken();
    this.apiKey = config.getApiKey();
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeConverter())
            .setPrettyPrinting()
            .create();
    this.api =
        new Retrofit.Builder()
            .baseUrl(AdobeioConstants.CLOUD_MANAGER_BASE_PATH)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonCustomConverterFactory.create(gson))
            .client(new OkHttpClient.Builder().addInterceptor(new RetryInterceptor()).build())
            .build()
            .create(apiClazz);
  }

  public static class OffsetDateTimeConverter
      implements JsonSerializer<OffsetDateTime>, JsonDeserializer<OffsetDateTime> {
    // Cloud manager uses this format, apparently.
    private static final DateTimeFormatter FORMATTER =
        new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").toFormatter();

    public JsonElement serialize(
        OffsetDateTime src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(FORMATTER.format(src));
    }

    @Override
    public OffsetDateTime deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return FORMATTER.parse(json.getAsString(), OffsetDateTime.FROM);
    }
  }

  /**
   * This wrapper is to take care of this case: when the deserialization fails due to
   * JsonParseException and the expected type is String, then just return the body string.
   */
  static class GsonResponseBodyConverterToString<T> implements Converter<ResponseBody, T> {
    private final Gson gson;
    private final Type type;

    GsonResponseBodyConverterToString(Gson gson, Type type) {
      this.gson = gson;
      this.type = type;
    }

    @Override
    public T convert(ResponseBody value) throws IOException {
      String returned = value.string();
      try {
        return gson.fromJson(returned, type);
      } catch (JsonParseException e) {
        return (T) returned;
      }
    }
  }

  static class GsonCustomConverterFactory extends Converter.Factory {
    private final Gson gson;
    private final GsonConverterFactory gsonConverterFactory;

    private GsonCustomConverterFactory(Gson gson) {
      if (gson == null) throw new NullPointerException("gson == null");
      this.gson = gson;
      this.gsonConverterFactory = GsonConverterFactory.create(gson);
    }

    public static GsonCustomConverterFactory create(Gson gson) {
      return new GsonCustomConverterFactory(gson);
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(
        Type type, Annotation[] annotations, Retrofit retrofit) {
      if (type.equals(String.class))
        return new GsonResponseBodyConverterToString<Object>(gson, type);
      else return gsonConverterFactory.responseBodyConverter(type, annotations, retrofit);
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(
        Type type,
        Annotation[] parameterAnnotations,
        Annotation[] methodAnnotations,
        Retrofit retrofit) {
      return gsonConverterFactory.requestBodyConverter(
          type, parameterAnnotations, methodAnnotations, retrofit);
    }
  }
}

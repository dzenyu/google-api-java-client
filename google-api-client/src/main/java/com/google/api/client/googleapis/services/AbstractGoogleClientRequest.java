/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.googleapis.services;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.MethodOverride;
import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.subscriptions.NotificationCallback;
import com.google.api.client.googleapis.subscriptions.Subscription;
import com.google.api.client.googleapis.subscriptions.SubscriptionHeaders;
import com.google.api.client.googleapis.subscriptions.SubscriptionUtils;
import com.google.api.client.googleapis.subscriptions.TypedNotificationCallback;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.UriTemplate;
import com.google.api.client.util.GenericData;
import com.google.common.base.Preconditions;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract Google client request for a {@link AbstractGoogleClient}.
 *
 * <p>
 * Implementation is not thread-safe.
 * </p>
 *
 * @param <T> type of the response
 *
 * @since 1.12
 * @author Yaniv Inbar
 */
public abstract class AbstractGoogleClientRequest<T> extends GenericData {

  /** Google client. */
  private final AbstractGoogleClient abstractGoogleClient;

  /** HTTP method. */
  private final String requestMethod;

  /** URI template for the path relative to the base URL. */
  private final String uriTemplate;

  /** HTTP content or {@code null} for none. */
  private final HttpContent httpContent;

  /** HTTP headers used for the Google client request. */
  private HttpHeaders requestHeaders = new HttpHeaders();

  /** HTTP headers of the last response or {@code null} before request has been executed. */
  private HttpHeaders lastResponseHeaders;

  /** Status code of the last response or {@code -1} before request has been executed. */
  private int lastStatusCode = -1;

  /** Status message of the last response or {@code null} before request has been executed. */
  private String lastStatusMessage;

  /** Whether to disable GZip compression of HTTP content. */
  private boolean disableGZipContent;

  /** Response class to parse into. */
  private Class<T> responseClass;

  /** Whether to subscribe to notifications. */
  private boolean isSubscribing;

  /** Callback for processing subscription notifications or {@code null} for none. */
  private NotificationCallback notificationCallback;

  /** Subscription headers from the last response or {@code null} for none. */
  private SubscriptionHeaders lastSubscriptionHeaders;

  /** Subscription details of the last response or {@code null} for none. */
  private Subscription lastSubscription;

  /** Media HTTP uploader or {@code null} for none. */
  private MediaHttpUploader uploader;

  /** Media HTTP downloader or {@code null} for none. */
  private MediaHttpDownloader downloader;

  /**
   * @param abstractGoogleClient Google client
   * @param requestMethod HTTP Method
   * @param uriTemplate URI template for the path relative to the base URL. If it starts with a "/"
   *        the base path from the base URL will be stripped out. The URI template can also be a
   *        full URL. URI template expansion is done using
   *        {@link UriTemplate#expand(String, String, Object, boolean)}
   * @param httpContent HTTP content or {@code null} for none
   * @param responseClass response class to parse into
   */
  protected AbstractGoogleClientRequest(AbstractGoogleClient abstractGoogleClient,
      String requestMethod, String uriTemplate, HttpContent httpContent, Class<T> responseClass) {
    this.responseClass = Preconditions.checkNotNull(responseClass);
    this.abstractGoogleClient = Preconditions.checkNotNull(abstractGoogleClient);
    this.requestMethod = Preconditions.checkNotNull(requestMethod);
    this.uriTemplate = Preconditions.checkNotNull(uriTemplate);
    this.httpContent = httpContent;
    // application name
    String applicationName = abstractGoogleClient.getApplicationName();
    if (applicationName != null) {
      requestHeaders.setUserAgent(applicationName);
    }
  }

  /** Returns whether to disable GZip compression of HTTP content. */
  public final boolean getDisableGZipContent() {
    return disableGZipContent;
  }

  /**
   * Sets whether to disable GZip compression of HTTP content.
   *
   * <p>
   * By default it is {@code false}.
   * </p>
   *
   * <p>
   * Overriding is only supported for the purpose of calling the super implementation and changing
   * the return type, but nothing else.
   * </p>
   */
  public AbstractGoogleClientRequest<T> setDisableGZipContent(boolean disableGZipContent) {
    this.disableGZipContent = disableGZipContent;
    return this;
  }

  /** Returns the HTTP method. */
  public final String getRequestMethod() {
    return requestMethod;
  }

  /** Returns the URI template for the path relative to the base URL. */
  public final String getUriTemplate() {
    return uriTemplate;
  }

  /** Returns the HTTP content or {@code null} for none. */
  public final HttpContent getHttpContent() {
    return httpContent;
  }

  /**
   * Returns the Google client.
   *
   * <p>
   * Overriding is only supported for the purpose of calling the super implementation and changing
   * the return type, but nothing else.
   * </p>
   */
  public AbstractGoogleClient getAbstractGoogleClient() {
    return abstractGoogleClient;
  }

  /** Returns the HTTP headers used for the Google client request. */
  public final HttpHeaders getRequestHeaders() {
    return requestHeaders;
  }

  /**
   * Sets the HTTP headers used for the Google client request.
   *
   * <p>
   * These headers are set on the request after {@link #buildHttpRequest} is called, this means that
   * {@link HttpRequestInitializer#initialize} is called first.
   * </p>
   *
   * <p>
   * Overriding is only supported for the purpose of calling the super implementation and changing
   * the return type, but nothing else.
   * </p>
   */
  public AbstractGoogleClientRequest<T> setRequestHeaders(HttpHeaders headers) {
    this.requestHeaders = headers;
    return this;
  }

  /**
   * Returns the HTTP headers of the last response or {@code null} before request has been executed.
   */
  public final HttpHeaders getLastResponseHeaders() {
    return lastResponseHeaders;
  }

  /**
   * Returns the status code of the last response or {@code -1} before request has been executed.
   */
  public final int getLastStatusCode() {
    return lastStatusCode;
  }

  /**
   * Returns the status message of the last response or {@code null} before request has been
   * executed.
   */
  public final String getLastStatusMessage() {
    return lastStatusMessage;
  }

  /** Returns the response class to parse into. */
  public final Class<T> getResponseClass() {
    return responseClass;
  }

  /** Returns whether to subscribe to notifications. */
  public final boolean isSubscribing() {
    return isSubscribing;
  }

  /** Returns the callback for processing subscription notifications or {@code null} for none. */
  public final NotificationCallback getNotificationCallback() {
    return notificationCallback;
  }

  /** Returns the notification delivery method or {@code null} for none. */
  public final String getNotificationDeliveryMethod() {
    return (String) requestHeaders.get(SubscriptionHeaders.SUBSCRIBE);
  }

  /** Returns the notification client token or {@code null} for none. */
  public final String getNotificationClientToken() {
    return (String) requestHeaders.get(SubscriptionHeaders.CLIENT_TOKEN);
  }

  /**
   * Sets the notification client token or {@code null} for none.
   *
   * <p>
   * Overriding is only supported for the purpose of changing visibility to public, but nothing
   * else.
   * </p>
   */
  public AbstractGoogleClientRequest<T> setNotificationClientToken(String notificationClientToken) {
    requestHeaders.set(SubscriptionHeaders.CLIENT_TOKEN, notificationClientToken);
    return this;
  }

  /**
   * Subscribes to unparsed notifications.
   *
   * <p>
   * A notification client token is randomly generated using
   * {@link SubscriptionUtils#generateRandomClientToken()}. You may override using
   * {@link #setNotificationClientToken(String)}.
   * </p>
   *
   * <p>
   * Overriding is only supported for the purpose of changing visibility to public, but nothing
   * else.
   * </p>
   *
   * @param notificationDeliveryMethod notification delivery method
   * @param notificationCallback notification callback or {@code null} for none
   */
  @SuppressWarnings("unchecked")
  protected AbstractGoogleClientRequest<T> subscribeUnparsed(
      String notificationDeliveryMethod, NotificationCallback notificationCallback) {
    this.notificationCallback = notificationCallback;
    requestHeaders.set(
        SubscriptionHeaders.SUBSCRIBE, Preconditions.checkNotNull(notificationDeliveryMethod));
    setNotificationClientToken(SubscriptionUtils.generateRandomClientToken());
    if (notificationCallback instanceof TypedNotificationCallback<?>) {
      ((TypedNotificationCallback<T>) notificationCallback).setDataType(responseClass);
    }
    isSubscribing = true;
    return this;
  }

  /**
   * Subscribes to parsed notifications.
   *
   * <p>
   * A notification client token is randomly generated using
   * {@link SubscriptionUtils#generateRandomClientToken()}. You may override using
   * {@link #setNotificationClientToken(String)}.
   * </p>
   *
   * <p>
   * Overriding is only supported for the purpose of changing visibility to public, but nothing
   * else.
   * </p>
   *
   * @param notificationDeliveryMethod notification delivery method
   * @param typedNotificationCallback typed notification callback or {@code null} for none
   */
  protected AbstractGoogleClientRequest<T> subscribe(
      String notificationDeliveryMethod, TypedNotificationCallback<T> typedNotificationCallback) {
    return subscribeUnparsed(notificationDeliveryMethod, typedNotificationCallback);
  }

  /** Returns the subscription details of the last response or {@code null} for none. */
  public final Subscription getLastSubscription() {
    return lastSubscription;
  }

  /** Returns the subscription headers from the last response or {@code null} for none. */
  public final SubscriptionHeaders getLastSubscriptionHeaders() {
    return lastSubscriptionHeaders;
  }

  /**
   * Sets the subscription details of the last response or {@code null} for none.
   *
   * <p>
   * Overriding is only supported for the purpose of calling the super implementation and changing
   * the return type, but nothing else.
   * </p>
   */
  protected AbstractGoogleClientRequest<T> setLastSubscription(Subscription lastSubscription) {
    this.lastSubscription = lastSubscription;
    return this;
  }

  /** Returns the media HTTP Uploader or {@code null} for none. */
  public final MediaHttpUploader getMediaHttpUploader() {
    return uploader;
  }

  /**
   * Initializes the media HTTP uploader based on the media content.
   *
   * @param mediaContent media content
   */
  protected final void initializeMediaUpload(AbstractInputStreamContent mediaContent) {
    HttpRequestFactory requestFactory = abstractGoogleClient.getRequestFactory();
    this.uploader = new MediaHttpUploader(
        mediaContent, requestFactory.getTransport(), requestFactory.getInitializer());
    this.uploader.setInitiationRequestMethod(requestMethod);
    if (httpContent != null) {
      this.uploader.setMetadata(httpContent);
    }
  }

  /** Returns the media HTTP downloader or {@code null} for none. */
  public final MediaHttpDownloader getMediaHttpDownloader() {
    return downloader;
  }

  /** Initializes the media HTTP downloader. */
  protected final void initializeMediaDownload() {
    HttpRequestFactory requestFactory = abstractGoogleClient.getRequestFactory();
    this.downloader =
        new MediaHttpDownloader(requestFactory.getTransport(), requestFactory.getInitializer());
  }

  /**
   * Creates a new instance of {@link GenericUrl} suitable for use against this service.
   *
   * <p>
   * Subclasses may override by calling the super implementation.
   * </p>
   *
   * @return newly created {@link GenericUrl}
   */
  public GenericUrl buildHttpRequestUrl() {
    return new GenericUrl(
        UriTemplate.expand(abstractGoogleClient.getBaseUrl(), uriTemplate, this, true));
  }

  /**
   * Create an {@link HttpRequest} suitable for use against this service.
   *
   * <p>
   * Subclasses may override by calling the super implementation.
   * </p>
   */
  public HttpRequest buildHttpRequest() throws Exception {
    Preconditions.checkArgument(uploader == null);
    HttpRequest httpRequest = getAbstractGoogleClient()
        .getRequestFactory().buildRequest(requestMethod, buildHttpRequestUrl(), httpContent);
    new MethodOverride().intercept(httpRequest);
    httpRequest.setParser(getAbstractGoogleClient().getObjectParser());
    // custom methods may use POST with no content but require a Content-Length header
    if (httpContent == null && requestMethod.equals(HttpMethods.POST)) {
      httpRequest.setContent(new EmptyContent());
    }
    httpRequest.getHeaders().putAll(requestHeaders);
    return httpRequest;
  }

  /**
   * Sends the request to the server and returns the raw {@link HttpResponse}.
   *
   * <p>
   * Callers are responsible for disconnecting the HTTP response by calling
   * {@link HttpResponse#disconnect}. Example usage:
   * </p>
   *
   * <pre>
     HttpResponse response = request.executeUnparsed();
     try {
       // process response..
     } finally {
       response.disconnect();
     }
   * </pre>
   *
   * <p>
   * Subclasses may override by calling the super implementation.
   * </p>
   *
   * @return the {@link HttpResponse}
   */
  public HttpResponse executeUnparsed() throws Exception {
    boolean throwExceptionOnExecuteError;
    HttpResponse response;
    if (uploader == null) {
      // normal request (not upload)
      HttpRequest request = buildHttpRequest();
      request.setEnableGZipContent(!disableGZipContent);
      throwExceptionOnExecuteError = request.getThrowExceptionOnExecuteError();
      request.setThrowExceptionOnExecuteError(false);
      response = request.execute();
    } else {
      // upload request
      GenericUrl httpRequestUrl = buildHttpRequestUrl();
      HttpRequest httpRequest = getAbstractGoogleClient()
          .getRequestFactory().buildRequest(requestMethod, httpRequestUrl, httpContent);
      throwExceptionOnExecuteError = httpRequest.getThrowExceptionOnExecuteError();
      uploader.setInitiationHeaders(new GoogleHeaders(requestHeaders));
      response = uploader.upload(httpRequestUrl);
      response.getRequest().setParser(getAbstractGoogleClient().getObjectParser());
    }
    // process response
    lastResponseHeaders = response.getHeaders();
    lastStatusCode = response.getStatusCode();
    lastStatusMessage = response.getStatusMessage();
    // process subscriptions
    if (isSubscribing) {
      lastSubscriptionHeaders = new SubscriptionHeaders(lastResponseHeaders);
      if (notificationCallback != null) {
        lastSubscription = new Subscription(
            lastSubscriptionHeaders.getSubscriptionID(), notificationCallback,
            lastSubscriptionHeaders.getClientToken());
        getAbstractGoogleClient().getSubscriptionStore().storeSubscription(lastSubscription);
      }
    }
    // process any error
    if (throwExceptionOnExecuteError && !response.isSuccessStatusCode()) {
      throw newExceptionOnError(response);
    }
    return response;
  }

  /**
   * Returns the exception to throw on an HTTP error response as defined by
   * {@link HttpResponse#isSuccessStatusCode()}.
   *
   * <p>
   * It is guaranteed that {@link HttpResponse#isSuccessStatusCode()} is {@code false}. Default
   * implementation is to call {@link HttpResponseException#HttpResponseException(HttpResponse)},
   * but subclasses may override.
   * </p>
   *
   * @param response HTTP response
   * @return exception to throw
   */
  protected Exception newExceptionOnError(HttpResponse response) {
    return new HttpResponseException(response);
  }

  /**
   * Sends the request to the server and returns the parsed response.
   *
   * <p>
   * Subclasses may override by calling the super implementation.
   * </p>
   *
   * @return parsed HTTP response
   */
  public T execute() throws Exception {
    HttpResponse response = executeUnparsed();
    // TODO(yanivi): remove workaround when feature is implemented
    // workaround for http://code.google.com/p/google-http-java-client/issues/detail?id=110
    if (Void.class.equals(responseClass)) {
      response.ignore();
      return null;
    }
    return response.parseAs(responseClass);
  }

  /**
   * Sends the request to the server and returns the content input stream of {@link HttpResponse}.
   *
   * <p>
   * Callers are responsible for closing the input stream after it is processed. Example sample:
   * </p>
   *
   * <pre>
     InputStream is = request.executeAsInputStream();
     try {
       // Process input stream..
     } finally {
       is.close();
     }
   * </pre>
   *
   * <p>
   * Subclasses may override by calling the super implementation.
   * </p>
   *
   * @return input stream of the response content
   */
  public InputStream executeAsInputStream() throws Exception {
    HttpResponse response = executeUnparsed();
    return response.getContent();
  }

  /**
   * Sends the request to the server and writes the content input stream of {@link HttpResponse}
   * into the given destination output stream.
   *
   * <p>
   * This method closes the content of the HTTP response from {@link HttpResponse#getContent()}.
   * </p>
   *
   * @param outputStream destination output stream
   */
  public final void download(OutputStream outputStream) throws Exception {
    if (downloader == null) {
      HttpResponse response = executeUnparsed();
      response.download(outputStream);
    } else {
      Preconditions.checkArgument(notificationCallback == null,
          "subscribing with a notification callback during media download is not yet implemented");
      downloader.download(buildHttpRequestUrl(), requestHeaders, outputStream);
    }
  }

  /**
   * Queues the request into the specified batch request container using the specified error class.
   *
   * <p>
   * Batched requests are then executed when {@link BatchRequest#execute()} is called.
   * </p>
   *
   * @param batchRequest batch request container
   * @param errorClass data class the unsuccessful response will be parsed into or
   *        {@code Void.class} to ignore the content
   * @param callback batch callback
   */
  public final <E> void queue(
      BatchRequest batchRequest, Class<E> errorClass, BatchCallback<T, E> callback)
      throws Exception {
    Preconditions.checkArgument(notificationCallback == null,
        "subscribing with a notification callback during batch is not yet implemented");
    batchRequest.queue(buildHttpRequest(), getResponseClass(), errorClass, callback);
  }
}
package dev.sanda.apifi.service.graphql_subcriptions.sse;

import graphql.ExecutionResult;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RequiredArgsConstructor
public class SseSubscriber implements Subscriber<ExecutionResult> {

  private final SseEmitter emitter;
  private Subscription subscription;

  @Override
  public void onSubscribe(Subscription s) {
    log.info("new SseSubscriber created");
    this.subscription = s;
    setCallbacks();
    request();
  }

  private void setCallbacks() {
    emitter.onCompletion(
      () -> {
        log.info("subscription completed, cancelling");
        subscription.cancel();
      }
    );
    emitter.onError(this::onError);
    emitter.onTimeout(
      () -> {
        log.info("subscription timed out, cancelling");
        subscription.cancel();
      }
    );
  }

  @Override
  public void onNext(ExecutionResult executionResult) {
    synchronized (emitter) {
      try {
        log.info("sending subscription payload");
        sendExecutionResultEvent(executionResult);
      } catch (Exception e) {
        fatalError(e);
      }
      request();
    }
  }

  @Override
  public void onError(Throwable t) {
    fatalError(t);
  }

  @Override
  public void onComplete() {
    try {
      sendCompletedEvent();
    } catch (Exception e) {
      fatalError(e);
    }
  }

  private void request() {
    Subscription subscription = this.subscription;
    if (subscription != null) {
      subscription.request(1);
    }
  }

  private void fatalError(Throwable t) {
    try {
      sendFatalErrorEvent(t);
      emitter.completeWithError(t);
    } catch (Exception ex) {
      log.error(ex.getMessage());
      ex.printStackTrace();
    }
  }

  private void sendExecutionResultEvent(ExecutionResult executionResult) {
    try {
      emitter.send(
        SseEmitter
          .event()
          .id(String.valueOf(System.currentTimeMillis()))
          .name("EXECUTION_RESULT")
          .data(executionResult.toSpecification())
      );
    } catch (IllegalStateException ex) {
      log.error(
        "Tried sending completed event to SSE but stream was already closed"
      );
    } catch (IOException e) {
      log.error(e.getMessage());
      e.printStackTrace();
    }
  }

  private void sendCompletedEvent() {
    try {
      emitter.send(
        SseEmitter
          .event()
          .id(String.valueOf(System.currentTimeMillis()))
          .name("COMPLETE")
          .data("{\"name\": \"COMPLETED_STREAM\"}")
      );
    } catch (IllegalStateException ex) {
      log.error(
        "Tried sending completed event to SSE but stream was already closed"
      );
    } catch (IOException e) {
      log.error(e.getMessage());
      e.printStackTrace();
    }
  }

  private void sendFatalErrorEvent(Throwable t) {
    try {
      emitter.send(
        SseEmitter
          .event()
          .id(String.valueOf(System.currentTimeMillis()))
          .name("FATAL_ERROR")
          .data("{\"MESSAGE\": \"" + t.getMessage() + "\"}")
      );
    } catch (IllegalStateException ex) {
      log.error(
        "Tried sending complete with error event to SSE but stream was already closed"
      );
    } catch (IOException e) {
      log.error(e.getMessage());
      e.printStackTrace();
    }
  }
}

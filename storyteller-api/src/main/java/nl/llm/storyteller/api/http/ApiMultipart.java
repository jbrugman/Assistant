package nl.llm.storyteller.api.http;

import io.javalin.http.Context;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ContentTooLargeResponse;
import io.javalin.http.UploadedFile;
import jakarta.servlet.ServletException;

final class ApiMultipart {
  private ApiMultipart() { }

  static UploadedFile file(Context context, String field) {
    if (!context.isMultipart()) {
      throw new IllegalArgumentException("Request must use multipart/form-data.");
    }
    try {
      return uploadedFile(context, field);
    } catch (ServletException ex) {
      // Jetty wraps multipart parser limit failures in ServletException rather than a 413 response.
      for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
        String message = cause.getMessage();
        if (cause instanceof IllegalStateException && message != null
          && message.startsWith("max ") && message.contains(" exceeded")) {
          throw new ContentTooLargeResponse("Multipart upload exceeds the configured size limit.");
        }
      }
      throw new BadRequestResponse("Invalid multipart request.");
    }
  }

  // Javalin's Kotlin API does not declare checked exceptions in its Java signature, but
  // Jetty propagates ServletException at runtime for malformed or oversized multipart data.
  // This declaration makes the checked catch above legal; static analysis cannot see that
  // runtime propagation, so suppress redundant-throws warnings only on this boundary.
  @SuppressWarnings({"java:S1130", "RedundantThrows"})
  private static UploadedFile uploadedFile(Context context, String field) throws ServletException {
    return context.uploadedFile(field);
  }
}

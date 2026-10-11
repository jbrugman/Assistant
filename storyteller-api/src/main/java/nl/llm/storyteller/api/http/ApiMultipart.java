package nl.llm.storyteller.api.http;

import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

final class ApiMultipart {
  private ApiMultipart() { }

  static UploadedFile file(Context context, String field) {
    if (!context.isMultipart()) {
      throw new IllegalArgumentException("Request must use multipart/form-data.");
    }
    try {
      return context.uploadedFile(field);
    } catch (Exception ex) {
      throw (RuntimeException) ex;
      // Jetty wraps multipart parser limit failures in ServletException rather than a 413 response.
    }
  }
}

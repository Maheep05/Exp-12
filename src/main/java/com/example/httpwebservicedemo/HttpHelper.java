package com.example.httpwebservicedemo;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Small HTTP client built on {@link HttpURLConnection} (part of the Android SDK, no extra library).
 *
 * <p>All methods block until the server answers, so they must be called from a background
 * thread. Calling them on the main thread throws {@code NetworkOnMainThreadException}.
 */
public final class HttpHelper {

    private static final int TIMEOUT_MS = 15_000;

    /** Status code, status message, body and round-trip time of one HTTP call. */
    public static final class Response {
        public final int code;
        public final String message;
        public final String body;
        public final long durationMs;

        Response(int code, String message, String body, long durationMs) {
            this.code = code;
            this.message = message;
            this.body = body;
            this.durationMs = durationMs;
        }

        public boolean isSuccessful() {
            return code >= 200 && code < 300;
        }
    }

    private HttpHelper() {
    }

    /** Sends an HTTP GET request and returns the server's response. */
    public static Response get(String url) throws IOException {
        return request("GET", url, null);
    }

    /** Sends an HTTP POST request with a JSON body and returns the server's response. */
    public static Response post(String url, String jsonBody) throws IOException {
        return request("POST", url, jsonBody);
    }

    private static Response request(String method, String urlString, String jsonBody)
            throws IOException {
        long start = SystemClock.elapsedRealtime();

        // 1. Open a connection to the URL
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            // 2. Configure the request: method, timeouts and headers
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            // 3. For POST, write the JSON body to the request's output stream
            if (jsonBody != null) {
                byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            // 4. Read the status code, then the body. 4xx/5xx bodies come on the error stream.
            int code = connection.getResponseCode();
            InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = in == null ? "" : readFully(in);
            String message = connection.getResponseMessage();

            return new Response(code, message == null ? "" : message, body,
                    SystemClock.elapsedRealtime() - start);
        } finally {
            // 5. Release the connection
            connection.disconnect();
        }
    }

    private static String readFully(InputStream in) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}

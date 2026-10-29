# Experiment: Accessing Web Services Using HTTP Requests in Android

| | |
|---|---|
| **Name** | Maheep Saluja |
| **USN** | 25MCAR0105 |
| **Project** | `HttpWebServiceDemo` |
| **Language** | Java (Android SDK) |

---

## 1. Aim

To build an Android application that connects to a web service over the internet, sends
**HTTP GET** and **HTTP POST** requests, receives the server's response, parses the JSON data and
shows the result on screen.

---

## 2. Concept

### 2.1 What is a web service?

A **web service** is a program on a server that other programs can use over a network. A client
(here, the Android app) sends a **request**, and the server sends back a **response**, usually as
**JSON** text.

Most modern web services follow the **REST** style:

- Each piece of data is a **resource** with its own URL, e.g. `https://jsonplaceholder.typicode.com/posts/1`.
- The **HTTP method** says what to do with that resource.
- Each request is **stateless**: it carries everything the server needs to answer it.

### 2.2 HTTP: request and response

HTTP (HyperText Transfer Protocol) is the protocol clients and web servers use to talk to each other.

**A request** has a method, a URL, headers and an optional body:

```http
POST /posts HTTP/1.1
Host: jsonplaceholder.typicode.com
Accept: application/json
Content-Type: application/json; charset=UTF-8

{ "name": "Maheep Saluja", "usn": "25MCAR0105", "experiment": "Access web services using HTTP requests" }
```

**A response** has a status code, headers and a body:

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=utf-8

{ "name": "Maheep Saluja", "usn": "25MCAR0105", "experiment": "...", "id": 101 }
```

### 2.3 HTTP methods

| Method | Purpose | Has a body? | Used in this app |
|---|---|---|---|
| `GET` | Read a resource | No | ✅ |
| `POST` | Create a new resource | Yes | ✅ |
| `PUT` | Replace a resource | Yes | |
| `DELETE` | Delete a resource | No | |

### 2.4 HTTP status codes

| Range | Meaning | Examples |
|---|---|---|
| `2xx` | Success | `200 OK`, `201 Created` |
| `3xx` | Redirection | `301 Moved Permanently` |
| `4xx` | Client error (bad request) | `400 Bad Request`, `404 Not Found` |
| `5xx` | Server error | `500 Internal Server Error` |

The app shows `2xx` results in **green** and `4xx`/`5xx` results in **red**.

### 2.5 JSON

**JSON** (JavaScript Object Notation) is the text format the web service uses for its data:

- `{ ... }` is an **object**: named fields, like `"id": 1`.
- `[ ... ]` is an **array**: an ordered list of values.

Android has the `org.json` library built in to read (parse) and write JSON.

---

## 3. Technology Behind It

| Technology | Role in this project |
|---|---|
| **`java.net.HttpURLConnection`** | Android's built-in HTTP client. Opens the connection, sets the method and headers, sends the body and reads the response. No third-party library is needed. |
| **`ExecutorService` (background thread)** | Network calls are slow and blocking. Android throws `NetworkOnMainThreadException` if they run on the main (UI) thread, so every request runs on a background thread. |
| **`Handler` + `Looper.getMainLooper()`** | Only the UI thread may change views. The background thread uses the `Handler` to send the result back to the UI thread. |
| **`org.json` (`JSONObject`, `JSONArray`, `JSONTokener`)** | Parses the response text into JSON objects/arrays and pretty-prints it. |
| **`ConnectivityManager`** | Checks whether the device has an internet connection before making a request. |
| **`INTERNET` permission** | Declared in `AndroidManifest.xml`. Without it, no socket can be opened. |
| **`usesCleartextTraffic="true"`** | From API 28, Android blocks plain `http://` by default. This setting allows it (e.g. for a local test server). `https://` always works. |
| **Material Components** | UI widgets: toggle buttons, text fields, progress bar and toolbar. |
| **Gradle + Android Gradle Plugin** | Build system (Gradle 9.6.0, AGP 9.4.1). |

**Build configuration:** `compileSdk` 37 · `targetSdk` 37 · `minSdk` 26 · Java 17

**Web service used:** [JSONPlaceholder](https://jsonplaceholder.typicode.com), a free, public REST
API for testing. It accepts `POST` requests and returns realistic responses, but does not actually
save the data. A new post always gets `"id": 101`.

### 3.1 How one request flows through the app

```text
User taps "Send request"
        │
        ▼
MainActivity (UI thread)
   • checks the URL is http:// or https://
   • checks the device is online
   • shows the progress bar
        │  executor.execute(...)
        ▼
Background thread → HttpHelper.get(url) / HttpHelper.post(url, body)
        │   1. open HttpURLConnection
        │   2. set method, timeouts, headers
        │   3. (POST) write JSON body           ──── HTTP request ────▶   Web service
        │   4. read status code + response body ◀─── HTTP response ────   (REST API)
        │   5. disconnect
        │  mainHandler.post(...)
        ▼
MainActivity (UI thread)
   • parses the body with org.json
   • shows "HTTP <code> <message> · <time> ms · <JSON summary>"
   • shows the pretty-printed response
```

### 3.2 Key code

**Making the request** (`HttpHelper.java`):

```java
HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
try {
    connection.setRequestMethod(method);                // "GET" or "POST"
    connection.setConnectTimeout(TIMEOUT_MS);
    connection.setReadTimeout(TIMEOUT_MS);
    connection.setRequestProperty("Accept", "application/json");

    if (jsonBody != null) {                             // POST only
        byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }
    }

    int code = connection.getResponseCode();
    // 4xx/5xx bodies come on the error stream, not the input stream
    InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
    ...
} finally {
    connection.disconnect();
}
```

**Running it off the UI thread** (`MainActivity.java`):

```java
executor.execute(() -> {                                 // background thread
    try {
        HttpHelper.Response response = isPost ? HttpHelper.post(url, body) : HttpHelper.get(url);
        mainHandler.post(() -> showResponse(response));  // back to the UI thread
    } catch (IOException e) {
        mainHandler.post(() -> showError(e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
});
```

**Permission** (`AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 4. Scenario to Demonstrate

> A student wants to submit their details to an online web service from their phone and also
> look up existing records on it. The app must handle three situations: reading a record that
> exists, creating a new record, and asking for a record that does not exist.

**Steps:**

1. Launch **HTTP Web Service Demo** on an emulator or phone with internet access. The student's
   name and USN are shown under the title.
2. **Read a record:** keep **GET** selected with URL `https://jsonplaceholder.typicode.com/posts/1`
   and tap **Send request**. The post with `id` 1 is returned.
3. **Create a record:** tap **POST**. The URL changes to `https://jsonplaceholder.typicode.com/posts`
   and a JSON body with the student's name and USN appears. Tap **Send request**. The server
   returns the same data plus a new `id`.
4. **Request a missing record:** tap **GET**, change the URL to
   `https://jsonplaceholder.typicode.com/posts/9999` and tap **Send request**. The server answers
   `404 Not Found`, and the status turns red.

Other behaviour you can try:

- **Invalid URL:** enter text without `http://` or `https://`. The URL field shows *"Enter a valid http:// or https:// URL"*.
- **No internet:** turn on airplane mode. The status shows *"Request failed: No internet connection"*.

---

## 5. Project Structure

```text
HttpWebServiceDemo/
├── app/                                        ← the Android application module
│   ├── build.gradle.kts                        ← module build script: SDK levels, Java 17, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml                 ← INTERNET permission, cleartext setting, launcher activity
│       ├── java/com/example/httpwebservicedemo/
│       │   ├── MainActivity.java               ← UI logic: input, threading, JSON parsing, showing results
│       │   └── HttpHelper.java                 ← HTTP client: get()/post() built on HttpURLConnection
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml           ← screen layout: toolbar, GET/POST toggle, URL, body, response
│           ├── values/
│           │   ├── strings.xml                 ← all text: student info, default URLs, POST body, messages
│           │   ├── colors.xml                  ← success (green) / error (red) / icon colours
│           │   └── themes.xml                  ← Material 3 app theme
│           ├── drawable/
│           │   └── ic_launcher_foreground.xml  ← globe icon (vector)
│           └── mipmap-anydpi-v26/
│               └── ic_launcher.xml             ← adaptive launcher icon
├── gradle/
│   ├── libs.versions.toml                      ← version catalog: AGP and library versions
│   └── wrapper/
│       ├── gradle-wrapper.jar                  ← downloads the right Gradle version automatically
│       └── gradle-wrapper.properties           ← Gradle 9.6.0 download URL + checksum
├── screenshots/                                ← output screenshots of the three test cases
├── build.gradle.kts                            ← root build script (declares the Android plugin)
├── settings.gradle.kts                         ← project name, repositories, included modules
├── gradle.properties                           ← Gradle/AndroidX settings
├── local.properties                            ← path to the Android SDK on this computer (not shared)
├── gradlew / gradlew.bat                       ← build from the command line (macOS/Linux / Windows)
└── README.md                                   ← this file
```

### Responsibilities of the two Java classes

| Class | Responsibility |
|---|---|
| `HttpHelper` | Knows **only** about HTTP. `get(url)` and `post(url, json)` return a `Response` with `code`, `message`, `body` and `durationMs`. They block until the server answers, so they must be called from a background thread. |
| `MainActivity` | Knows **only** about the screen. It reads user input, validates the URL, checks connectivity, starts the background request, parses the JSON and updates the views. |

### Dependencies

| Library | Version | Used for |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.8.0 | `AppCompatActivity` |
| `com.google.android.material:material` | 1.14.0 | Material 3 widgets |
| `androidx.activity:activity` | 1.13.0 | Edge-to-edge display |

`HttpURLConnection` and `org.json` are part of the Android SDK, so they need no dependency.

---

## 6. Test Cases and Output

| # | Test case | Method | URL | Input | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|
| 1 | Read an existing resource | `GET` | `https://jsonplaceholder.typicode.com/posts/1` | — | `200 OK` and post with `id` 1 | `HTTP 200 OK · 1502 ms · JSON object, 4 fields` | ✅ Pass |
| 2 | Create a resource with the student's details | `POST` | `https://jsonplaceholder.typicode.com/posts` | `{"name": "Maheep Saluja", "usn": "25MCAR0105", "experiment": "..."}` | `201 Created`; same data echoed back with a new `id` | `HTTP 201 Created · 488 ms · JSON object, 4 fields`, `"id": 101` | ✅ Pass |
| 3 | Request a resource that does not exist | `GET` | `https://jsonplaceholder.typicode.com/posts/9999` | — | `404 Not Found` shown as an error (red) | `HTTP 404 Not Found · 177 ms · JSON object, 0 fields`, body `{}` | ✅ Pass |
<img width="540" height="1200" alt="test_case_2_post" src="https://github.com/user-attachments/assets/7f7a1532-9795-4203-ae85-df1dcddb5466" />
<img width="540" height="1200" alt="test_case_1_get" src="https://github.com/user-attachments/assets/dc21dd1d-bdd2-46bc-ac76-8a0ef901e8de" />
<img width="1760" height="1500" alt="all_test_cases" src="https://github.com/user-attachments/assets/8c214618-b8a9-4da1-97c7-1e76e2eeb3db" />

*Response times depend on the network and will differ on each run.*

### All three test cases

![Output of all three test cases](screenshots/all_test_cases.png)

### Individual screenshots

| Test Case 1: GET (200 OK) | Test Case 2: POST (201 Created) | Test Case 3: GET (404 Not Found) |
|---|---|---|
<img width="540" height="1200" alt="test_case_3_not_found" src="https://github.com/user-attachments/assets/367ec9c0-8c0c-461c-985b-9c2324ee9d6e" />

---

## 7. How to Run

**Android Studio**

1. **File → Open** and select the `HttpWebServiceDemo` folder.
2. Wait for Gradle sync to finish.
3. Start an emulator (or connect a phone with USB debugging on) and click **Run ▶**.

**Command line**

```bash
./gradlew assembleDebug                                   # builds app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk  # installs it on the running emulator/phone
```

The emulator or phone needs internet access.

---

## 8. Result

An Android application was built that accesses a REST web service using HTTP requests. It sends
`GET` and `POST` requests with `HttpURLConnection` on a background thread, receives and parses
the JSON responses with `org.json`, and shows the status code, response time and formatted
response on the screen. All three test cases (successful read, successful create, and a
`404` error) gave the expected output.

## 9. Conclusion

- Android apps reach web services through HTTP. `HttpURLConnection` is enough for simple
  requests, and libraries such as Retrofit, OkHttp or Volley build on the same ideas.
- Network code must run **off the main thread**. Results must go **back to the main thread**
  before the UI is updated.
- The app must declare the `INTERNET` permission and must handle both success (`2xx`) and error
  (`4xx`/`5xx`) responses. Error bodies are read from `getErrorStream()`.
- JSON is the common data format between the app and the service, and `org.json` parses it
  without extra libraries.

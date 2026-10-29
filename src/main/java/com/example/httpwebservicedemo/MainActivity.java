package com.example.httpwebservicedemo;

import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Network calls run on this background thread; results are posted back to the UI thread.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MaterialButtonToggleGroup methodGroup;
    private TextInputLayout urlLayout;
    private TextInputEditText urlInput;
    private TextInputLayout bodyLayout;
    private TextInputEditText bodyInput;
    private Button sendButton;
    private LinearProgressIndicator progress;
    private TextView statusText;
    private TextView responseText;
    private ColorStateList defaultStatusColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        methodGroup = findViewById(R.id.methodGroup);
        urlLayout = findViewById(R.id.urlLayout);
        urlInput = findViewById(R.id.urlInput);
        bodyLayout = findViewById(R.id.bodyLayout);
        bodyInput = findViewById(R.id.bodyInput);
        sendButton = findViewById(R.id.sendButton);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.statusText);
        responseText = findViewById(R.id.responseText);
        defaultStatusColor = statusText.getTextColors();

        methodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                onMethodChanged(checkedId == R.id.btnPost);
            }
        });
        sendButton.setOnClickListener(v -> sendRequest());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                sendRequest();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void onMethodChanged(boolean isPost) {
        bodyLayout.setVisibility(isPost ? View.VISIBLE : View.GONE);

        // Swap in the matching sample endpoint, unless the user typed their own URL
        String url = textOf(urlInput);
        if (url.equals(getString(R.string.default_get_url))
                || url.equals(getString(R.string.default_post_url))) {
            urlInput.setText(isPost ? R.string.default_post_url : R.string.default_get_url);
        }
    }

    private void sendRequest() {
        String url = textOf(urlInput);
        if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) {
            urlLayout.setError(getString(R.string.error_invalid_url));
            return;
        }
        urlLayout.setError(null);

        if (!isOnline()) {
            showError(getString(R.string.error_no_network));
            return;
        }

        boolean isPost = methodGroup.getCheckedButtonId() == R.id.btnPost;
        String body = textOf(bodyInput);
        hideKeyboard();
        setLoading(true, isPost ? "POST" : "GET");

        executor.execute(() -> {
            try {
                HttpHelper.Response response = isPost
                        ? HttpHelper.post(url, body)
                        : HttpHelper.get(url);
                mainHandler.post(() -> showResponse(response));
            } catch (IOException e) {
                mainHandler.post(() -> showError(
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    private void showResponse(HttpHelper.Response response) {
        if (isDestroyed()) {
            return;
        }
        setLoading(false, null);

        Object json = parseJson(response.body);
        statusText.setTextColor(getColor(
                response.isSuccessful() ? R.color.status_success : R.color.status_error));
        statusText.setText(getString(R.string.status_ok, response.code, response.message,
                response.durationMs, describe(json, response.body)));
        responseText.setText(prettyPrint(json, response.body));
    }

    private void showError(String message) {
        if (isDestroyed()) {
            return;
        }
        setLoading(false, null);
        statusText.setTextColor(getColor(R.color.status_error));
        statusText.setText(getString(R.string.status_error, message));
        responseText.setText("");
    }

    private void setLoading(boolean loading, String method) {
        sendButton.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        if (loading) {
            statusText.setTextColor(defaultStatusColor);
            statusText.setText(getString(R.string.status_loading, method));
            responseText.setText("");
        }
    }

    /** Parses the body with org.json; returns a JSONObject, a JSONArray, or null if it isn't JSON. */
    private static Object parseJson(String body) {
        try {
            Object value = new JSONTokener(body.trim()).nextValue();
            if (value instanceof JSONObject || value instanceof JSONArray) {
                return value;
            }
        } catch (JSONException ignored) {
            // Not JSON: shown as plain text
        }
        return null;
    }

    private static String describe(Object json, String body) {
        if (json instanceof JSONArray) {
            return "JSON array, " + ((JSONArray) json).length() + " items";
        }
        if (json instanceof JSONObject) {
            return "JSON object, " + ((JSONObject) json).length() + " fields";
        }
        return body.length() + " chars";
    }

    private static String prettyPrint(Object json, String body) {
        try {
            String pretty = json instanceof JSONArray
                    ? ((JSONArray) json).toString(2)
                    : json instanceof JSONObject ? ((JSONObject) json).toString(2) : body;
            // org.json escapes "/" as "\/"; undo that so URLs stay readable
            return pretty.replace("\\/", "/");
        } catch (JSONException e) {
            return body;
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(sendButton.getWindowToken(), 0);
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}

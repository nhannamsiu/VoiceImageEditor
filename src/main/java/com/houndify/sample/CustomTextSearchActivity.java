package com.houndify.sample;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hound.android.sdk.AsyncTextSearch;
import com.hound.android.sdk.Search;
import com.hound.android.sdk.TextSearchListener;
import com.hound.android.sdk.VoiceSearchInfo;
import com.hound.android.sdk.VoiceSearchState;
import com.hound.android.sdk.util.HoundRequestInfoFactory;
import com.hound.core.model.sdk.HoundRequestInfo;
import com.hound.core.model.sdk.HoundResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public class CustomTextSearchActivity extends AppCompatActivity {
    private static final String LOG_TAG = CustomTextSearchActivity.class.getSimpleName();

    private TextView textView;
    private Button button;
    private EditText editText;

    private AsyncTextSearch asyncTextSearch;

    private LocationManager locationManager;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Search.setDebug(false);

        setContentView(R.layout.activity_textsearch);

        textView = findViewById(R.id.textView);
        button = findViewById(R.id.button);
        editText = findViewById(R.id.editText);
        editText.setText("What is the weather");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (asyncTextSearch == null) {
                    resetUIState();
                    startSearch();
                }
                else {
                    // voice search has already started.
                    if (asyncTextSearch.getState() == VoiceSearchState.STATE_STARTED) {
                        asyncTextSearch.abort();
                    }

                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (asyncTextSearch != null) {
            asyncTextSearch.abort();
        }
    }

    private HoundRequestInfo getHoundRequestInfo() {
        final HoundRequestInfo requestInfo = HoundRequestInfoFactory.getDefault(this);

        Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        requestInfo.setUserId("User ID");
        requestInfo.setRequestId(UUID.randomUUID().toString());
        if (location != null) {
            requestInfo.setLatitude(location.getLatitude());
            requestInfo.setLongitude(location.getLongitude());
            requestInfo.setPositionHorizontalAccuracy((double) location.getAccuracy());
        }

        return requestInfo;
    }

    private void startSearch() {
        if (asyncTextSearch != null) {
            return; // We are already searching
        }

        AsyncTextSearch.Builder builder = new AsyncTextSearch.Builder()
                .setRequestInfo(getHoundRequestInfo())
                .setClientId(ClientCredentialsUtil.getClientId(this))
                .setClientKey(ClientCredentialsUtil.getClientKey(this))
                .setListener(textSearchListener)
                .setQuery(editText.getText().toString());

        asyncTextSearch = builder.build();

        textView.setText("Waiting for response...");
        button.setText("Stop Search");

        asyncTextSearch.start();
    }

    private void resetUIState() {
        button.setEnabled(true);
        button.setText("Submit text");
    }

    private final TextSearchListener textSearchListener = new TextSearchListener() {

        @Override
        public void onResponse(final HoundResponse response, final VoiceSearchInfo info) {
            asyncTextSearch = null;
            resetUIState();

            // Make sure the request succeeded with OK
            if (response.getStatus().equals(HoundResponse.Status.OK)) {

                textView.setText("Received response...displaying the JSON");

                // We put pretty printing JSON on a separate thread as the server JSON can be
                // quite large and will stutter the UI

                // Not meant to be configuration change proof, this is just a demo
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String message;
                        try {
                            message = "Response\n\n" +
                                    new JSONObject(info.getContentBody()).toString(4);
                        }
                        catch (final JSONException ex) {
                            textView.setText("Bad JSON\n\n" + response);
                            message = "Bad JSON\n\n" + response;
                        }

                        final String finalMessage = message;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(finalMessage);
                            }
                        });
                    }
                }).start();
            }
            else {
                textView.setText("Request failed with: " + response.getErrorMessage());
            }
        }

        @Override
        public void onError(final Exception ex, final VoiceSearchInfo info) {
            asyncTextSearch = null;
            resetUIState();
            textView.setText(exceptionToString(ex));
        }

        @Override
        public void onAbort(final VoiceSearchInfo info) {
            asyncTextSearch = null;
            resetUIState();
            textView.setText("Aborted");
        }
    };

    private static String exceptionToString(final Exception ex) {
        try {
            final StringWriter sw = new StringWriter(1024);
            final PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.close();
            return sw.toString();
        }
        catch (final Exception e) {
            return "";
        }
    }
}

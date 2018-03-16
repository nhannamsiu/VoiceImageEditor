package com.houndify.sample;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.hound.android.libphs.PhraseSpotterStream;
import com.hound.android.sdk.Search;
import com.hound.android.sdk.VoiceSearch;
import com.hound.android.sdk.VoiceSearchInfo;
import com.hound.android.sdk.VoiceSearchListener;
import com.hound.android.sdk.audio.SimpleAudioByteStreamSource;
import com.hound.android.sdk.util.HoundRequestInfoFactory;
import com.hound.core.model.sdk.HoundRequestInfo;
import com.hound.core.model.sdk.HoundResponse;
import com.hound.core.model.sdk.PartialTranscript;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomVoiceSearchActivity extends AppCompatActivity {

    private LocationManager locationManager;

    private VoiceSearch voiceSearch;

    private TextView statusTextView;
    private TextView contentTextView;
    private Button btnSearch;
    private ImageView ImageView;
    private String responseText = "";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_voice_search_phrase);

        statusTextView = findViewById(R.id.status_text_view);
        contentTextView = findViewById(R.id.textView);
        ImageView = findViewById(R.id.imageView);

        btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (voiceSearch == null) {
                    startSearch(new SimpleAudioByteStreamSource(), false);
                }
                else {
                    voiceSearch.stopRecording();
                }
            }
        });

        Search.setDebug(true);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void startSearch(InputStream inputStream, boolean enforceWakeUpPattern) {
        if (voiceSearch != null) {
            return; // We are already searching
        }

        voiceSearch =
                new VoiceSearch.Builder().setRequestInfo(getHoundRequestInfo(enforceWakeUpPattern))
                        .setClientId(ClientCredentialsUtil.getClientId(this))
                        .setClientKey(ClientCredentialsUtil.getClientKey(this))
                        .setListener(voiceListener)
                        .setAudioSource(inputStream)
                        .build();

        statusTextView.setText("Listening...");
        btnSearch.setText("Stop Listening");

        voiceSearch.start();
    }

    /**
     * Implementation of the VoiceSearchListener interface used for receiving search state
     * information
     * and the final search results.
     */
    private final VoiceSearchListener voiceListener = new VoiceSearchListener() {

        /**
         * Called every time a new partial transcription is received from the Hound server.
         * This is used for providing feedback to the user of the server's interpretation of
         * their query.
         *
         * @param transcript
         */
        @Override
        public void onTranscriptionUpdate(final PartialTranscript transcript) {
            switch (voiceSearch.getState()) {
            case STATE_STARTED:
                statusTextView.setText("Listening...");
                break;

            case STATE_SEARCHING:
                statusTextView.setText("Receiving...");
                break;

            default:
                statusTextView.setText("Unknown");
                break;
            }
            responseText = transcript.getPartialTranscript();
            contentTextView.setText("Transcription:\n" + responseText);
        }

        @Override
        public void onResponse(final HoundResponse response, final VoiceSearchInfo info) {

            voiceSearch = null;

            // Make sure the request succeeded with OK
            if (!response.getStatus().equals(HoundResponse.Status.OK)) {
                statusTextView.setText("Something went wrong");
                contentTextView.setText("Request failed with: " + response.getErrorMessage());
                return;
            }

            statusTextView.setText("Received Response");
            contentTextView.setText(responseText);
            btnSearch.setText("Search");

            if (response.getResults().isEmpty()) {
                contentTextView.setText("No Results");
                btnSearch.setText("Search");
                return;
            }

            queryImages();

        }

        /**
         * Called if the search fails do to some kind of error situation.
         *
         * @param ex
         * @param info
         */
        @Override
        public void onError(final Exception ex, final VoiceSearchInfo info) {
            voiceSearch = null;

            statusTextView.setText("Something went wrong");
            contentTextView.setText(exceptionToString(ex));
        }

        /**
         * Called when the recording phase is completed.
         */
        @Override
        public void onRecordingStopped() {
            statusTextView.setText("Receiving...");
        }

        /**
         * Called if the user aborted the search.
         *
         * @param info
         */
        @Override
        public void onAbort(final VoiceSearchInfo info) {
            voiceSearch = null;
            statusTextView.setText("Aborted");
        }
    };

    private HoundRequestInfo getHoundRequestInfo(boolean enforceWakeUpPattern) {
        final HoundRequestInfo requestInfo = HoundRequestInfoFactory.getDefault(this);

        // Client App is responsible for providing a UserId for their users which is meaningful
        // to the client.
        requestInfo.setUserId("User ID");
        // Each request must provide a unique request ID.
        requestInfo.setRequestId(UUID.randomUUID().toString());

        // Providing the user's location is useful for geographic queries, such as, "Show me
        // restaurants near me".
        /*setLocation(
                requestInfo,
                locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER));*/

        if (enforceWakeUpPattern) {
            // In 'Instant Trigger Mode', we expect the wake-up phrase to be always there.
            // If mismatch happens, the Houndify platform may consider the phrase spotting
            // to be false-positive, and simply ignore the voice search.
            requestInfo.setWakeUpPattern("\"OK Hound\"");
        }
        else {
            requestInfo.setWakeUpPattern(PhraseSpotterStream.PATTERN);
        }

        return requestInfo;
    }

    public static void setLocation(final HoundRequestInfo requestInfo, final Location location) {
        if (location != null) {
            requestInfo.setLatitude(location.getLatitude());
            requestInfo.setLongitude(location.getLongitude());
            requestInfo.setPositionHorizontalAccuracy((double) location.getAccuracy());
        }
    }

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

    private void queryImages() {
        AssetManager assetManager = getAssets();
        responseText = responseText.replaceAll(" ","").toLowerCase();
        try {
            InputStream is = assetManager.open(responseText+"/1.jpg");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            ImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
        }

    }
}

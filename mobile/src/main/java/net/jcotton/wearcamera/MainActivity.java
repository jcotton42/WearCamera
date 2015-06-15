package net.jcotton.wearcamera;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;


public class MainActivity extends Activity implements
                                           GoogleApiClient.ConnectionCallbacks,
                                           GoogleApiClient.OnConnectionFailedListener {
    private static final int TAKE_PICTURE = 100;
    private static final String TAG = "MainActivity";

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";

    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;

    /**
     * Google API client.
     */
    private GoogleApiClient googleApiClient;

    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean isInResolution;

    /**
     * Called when the activity is starting. Restores the activity state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            isInResolution = savedInstanceState.getBoolean(KEY_IN_RESOLUTION, false);
        }
    }

    /**
     * Called when the Activity is made visible.
     * A connection to Play Services need to be initiated as
     * soon as the activity is visible. Registers {@code ConnectionCallbacks}
     * and {@code OnConnectionFailedListener} on the
     * activities itself.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if(googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        googleApiClient.connect();
    }

    /**
     * Called when activity gets invisible. Connection to Play Services needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onStop() {
        if(googleApiClient != null) {
            googleApiClient.disconnect();
        }
        super.onStop();
    }

    /**
     * Saves the resolution state.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, isInResolution);
    }

    /**
     * Handles Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
            case TAKE_PICTURE:
                if(resultCode == RESULT_OK) {
                    final Uri uri = data.getData();
                    getContentResolver().notifyChange(uri, null);
                    try {
                        final Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        int p = 0;
                        final int s = bmp.getWidth() * bmp.getHeight();
                        for(int x = 0; x < bmp.getWidth(); x++)
                            for(int y = 0; y < bmp.getHeight(); y++)
                                p += bmp.getPixel(x, y);
                        p /= s;
                        final PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/color");
                        final DataMap map = putDataMapRequest.getDataMap();
                        map.putInt("color", p);
                        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest());
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
        }
    }

    private void retryConnecting() {
        isInResolution = false;
        if(!googleApiClient.isConnecting()) {
            googleApiClient.connect();
        }
    }

    /**
     * Called when {@code googleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "GoogleApiClient connected");
        takePhoto();
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE);
    }

    /**
     * Called when {@code googleApiClient} connection is suspended.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
        retryConnecting();
    }

    /**
     * Called when {@code googleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if(!result.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    result.getErrorCode(), this, 0, new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            retryConnecting();
                        }
                    }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if(isInResolution) {
            return;
        }
        isInResolution = true;
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch(SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            retryConnecting();
        }
    }
}

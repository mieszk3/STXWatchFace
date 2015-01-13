package com.mieszkostelmach.stxwatchface.activity;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.companion.WatchFaceCompanion;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.mieszkostelmach.stxwatchface.R;

/**
 * @author Mieszko Stelmach
 *         Created on 04-01-2015.
 */
public class STXWatchFaceConfigActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataApi.DataItemResult>, View.OnClickListener {

    private static final String TAG = "STXWatchFaceConfig";

    private static final String KEY_WATCH_FACE = "WATCH_FACE";
    private static final String PATH_WITH_FEATURE = "/watch_face_config/STXWatchFace";
    private GoogleApiClient mGoogleApiClient;
    private String mPeerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_face_config);

        this.mPeerId = getIntent().getStringExtra(WatchFaceCompanion.EXTRA_PEER_ID);
        this.mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + bundle);
        }

        if (mPeerId != null) {
            Uri.Builder builder = new Uri.Builder();
            Uri uri = builder.scheme("wear").path(PATH_WITH_FEATURE).authority(mPeerId).build();
            Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(this);
        } else {
            displayNoConnectedDeviceDialog();
        }
    }

    private void displayNoConnectedDeviceDialog() {
        Toast.makeText(this, R.string.no_device_connected, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionFailed: " + connectionResult);
        }
    }

    @Override
    public void onResult(DataApi.DataItemResult dataItemResult) {
        if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
            DataItem configDataItem = dataItemResult.getDataItem();
            DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
            DataMap config = dataMapItem.getDataMap();
            setUpSettings(config);
        } else {
            // If DataItem with the current config can't be retrieved.
            setUpSettings(null);
        }
    }

    private void setUpSettings(DataMap config) {
        if (config == null || !config.containsKey(KEY_WATCH_FACE)) {
            //select default
            findViewById(R.id.super_hero_face).setSelected(true);
            findViewById(R.id.super_woman_hero_face).setSelected(false);
            findViewById(R.id.super_hero_alpha_face).setSelected(false);
            findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
        } else {
            String faceSelected = config.getString(KEY_WATCH_FACE);
            switch(faceSelected) {
                case "superhero":
                    findViewById(R.id.super_hero_face).setSelected(true);
                    findViewById(R.id.super_woman_hero_face).setSelected(false);
                    findViewById(R.id.super_hero_alpha_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                    break;
                case "superwhero":
                    findViewById(R.id.super_hero_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_face).setSelected(true);
                    findViewById(R.id.super_hero_alpha_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                    break;
                case "superhero_alpha":
                    findViewById(R.id.super_hero_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_face).setSelected(false);
                    findViewById(R.id.super_hero_alpha_face).setSelected(true);
                    findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                    break;
                case "superwhero_alpha":
                    findViewById(R.id.super_hero_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_face).setSelected(false);
                    findViewById(R.id.super_hero_alpha_face).setSelected(false);
                    findViewById(R.id.super_woman_hero_alpha_face).setSelected(true);
                    break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        v.setSelected(true);
        String tag = (String) v.getTag();
        int id = v.getId();
        switch(id) {
            case R.id.super_hero_face:
                findViewById(R.id.super_woman_hero_face).setSelected(false);
                findViewById(R.id.super_hero_alpha_face).setSelected(false);
                findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                break;
            case R.id.super_woman_hero_face:
                findViewById(R.id.super_hero_face).setSelected(false);
                findViewById(R.id.super_hero_alpha_face).setSelected(false);
                findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                break;
            case R.id.super_hero_alpha_face:
                findViewById(R.id.super_hero_face).setSelected(false);
                findViewById(R.id.super_woman_hero_face).setSelected(false);
                findViewById(R.id.super_woman_hero_alpha_face).setSelected(false);
                break;
            case R.id.super_woman_hero_alpha_face:
                findViewById(R.id.super_hero_face).setSelected(false);
                findViewById(R.id.super_woman_hero_face).setSelected(false);
                findViewById(R.id.super_hero_alpha_face).setSelected(false);
                break;
        }
        sendConfig(tag);
    }

    private void sendConfig(String setting) {
        if (mPeerId != null) {
            DataMap config = new DataMap();
            config.putString(KEY_WATCH_FACE, setting);
            byte[] rawData = config.toByteArray();
            Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, PATH_WITH_FEATURE, rawData);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sent watch face config message: " + KEY_WATCH_FACE + " -> " + setting);
            }
        }
    }
}

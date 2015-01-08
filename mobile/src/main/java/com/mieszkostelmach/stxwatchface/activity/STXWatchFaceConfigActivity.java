package com.mieszkostelmach.stxwatchface.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.support.wearable.companion.WatchFaceCompanion;
import android.widget.TextView;

import com.mieszkostelmach.stxwatchface.R;

/**
 * @author Mieszko Stelmach
 *         Created on 04-01-2015.
 */
public class STXWatchFaceConfigActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_face_config);

        ComponentName name = getIntent().getParcelableExtra(WatchFaceCompanion.EXTRA_WATCH_FACE_COMPONENT);
        TextView label = (TextView) findViewById(R.id.label);
        label.setText(label.getText() + " (" + name.getClassName() + ")");
    }
}

// Copyright 2015, Blackbuck Computing Inc. and/or its subsidiaries

package mobi.blackbuck.measure;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;

import java.text.DecimalFormat;

public class MainActivity extends Activity implements View.OnClickListener {
    /** Class name for log messages. */
    private final static String TAG = "UI";

    /** Bundle key for saving/restoring the toolbar title. */
    private final static String BUNDLE_KEY_TOOLBAR_TITLE = "title";

    /** The toolbar view control. */
    private Toolbar toolbar;

    /** The helper class used to toggle the left navigation drawer open and closed. */
    private ActionBarDrawerToggle drawerToggle;

    private Button mButtonHowzInternets;

    private Intent mServiceIntent;

    private TextView mCheckingWhatNwk;
    private TextView mSpeedResult;
    private TextView mCongestionResult;
    private ImageButton mEmailButton;
    private String mEmailBodyText = "";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Button init
        mButtonHowzInternets = (Button)findViewById(R.id.howzMyInternet);
        mButtonHowzInternets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                resetUiForRerun();

                // Quick check of current network conditions.
                ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
                    Toast.makeText(getApplicationContext(), "Why are we offline? Jump in, the water's fine!", Toast.LENGTH_SHORT).show();
                }

                Button bt = (Button) v;
                bt.setEnabled(false);
                bt.setText("Hold tight :)");

                mServiceIntent = new Intent(getApplicationContext(), MeasurementService.class);
                mServiceIntent.setAction(MeasurementService.ACTION_LATENCY);
                startService(mServiceIntent);
            }
        });

        mEmailButton = (ImageButton)findViewById(R.id.emailItButton);
        mEmailButton.setVisibility(View.INVISIBLE);
        mEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mEmailBodyText.equalsIgnoreCase("")) {
                    Intent sendEmailIntent = new Intent(Intent.ACTION_SEND);
                    sendEmailIntent.setType("message/rfc822");
                    sendEmailIntent.putExtra(Intent.EXTRA_CC, new String[]{"support@blackbuck.mobi"});
                    sendEmailIntent.putExtra(Intent.EXTRA_SUBJECT, "Results of speed test");
                    sendEmailIntent.putExtra(Intent.EXTRA_TEXT, mEmailBodyText);
                    startActivity(Intent.createChooser(sendEmailIntent, "Share your results!"));
                }
            }
        });

        mCheckingWhatNwk = (TextView)findViewById(R.id.whatNetwork);
        mCheckingWhatNwk.setVisibility(View.INVISIBLE);

        mSpeedResult = (TextView)findViewById(R.id.speedText);
        mCongestionResult = (TextView)findViewById(R.id.congestionText);

        resetUiForRerun();

        // The filter's action is BROADCAST_ACTION
        IntentFilter mStatusIntentFilter = new IntentFilter(MeasurementService.BROADCAST_ACTION_LATRUN_PROGRESS);
        mStatusIntentFilter.addAction(MeasurementService.BROADCAST_ACTION_BWRUN_COMPLETED);
        mStatusIntentFilter.addAction(MeasurementService.BROADCAST_ACTION_PROGRESS_1);
        MeasurementCompleteReceiver mReceiver = new MeasurementCompleteReceiver();
        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, mStatusIntentFilter);
    }

    private void resetUiForRerun() {
        mSpeedResult.setVisibility(View.INVISIBLE);
        mSpeedResult.setText(R.string.speed_results_placeholder);

        mCongestionResult.setVisibility(View.INVISIBLE);
        mCongestionResult.setText(R.string.congestion_results_placeholder);

        mEmailButton.setVisibility(View.INVISIBLE);
        mEmailBodyText = "";
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here excluding the home button.
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        super.onSaveInstanceState(bundle);
        // Save the title so it will be restored properly to match the view loaded when rotation
        // was changed or in case the activity was destroyed.
        if (toolbar != null) {
            bundle.putCharSequence(BUNDLE_KEY_TOOLBAR_TITLE, toolbar.getTitle());
        }
    }

    @Override
    public void onClick(final View view) {
        /*
        if (view == signOutButton) {
            // The user is currently signed in with a provider. Sign out of that provider.
            identityManager.signOut();
            // Show the sign-in button and hide the sign-out button.
            signOutButton.setVisibility(View.INVISIBLE);
            signInButton.setVisibility(View.VISIBLE);

            return;
        }
        if (view == signInButton) {
            // Start the sign-in activity. Do not finish this activity to allow the user to navigate back.
            startActivity(new Intent(this, SignInActivity.class));

            return;
        }

       */

    }

    @Override
    protected void onResume() {
        super.onResume();

        updateColor();
        syncUserSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void syncUserSettings() {
        //TODO: Stub
    }

    public void updateColor() {
        //TODO: Stub
    }

    // Broadcast receiver for receiving status updates from the IntentService
    private class MeasurementCompleteReceiver extends BroadcastReceiver {
        // Prevents instantiation
        private void MeasurementCompleteReceiver() {
        }

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equalsIgnoreCase(MeasurementService.BROADCAST_ACTION_PROGRESS_1)) {
                String networkName = intent.getStringExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_NETWORKNAME);
                String bearerDesc = intent.getStringExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_BEARERDESC);
                String signalStrength = intent.getStringExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_SIGNALSTRENGTH);

                String message = "";
                if (signalStrength.equalsIgnoreCase("")) {
                    message = String.format("Using %s (%s)..", networkName.toUpperCase(), bearerDesc);;
                } else  {
                    message = String.format("Using %s (%s with %s signal)..", networkName.toUpperCase(), bearerDesc, signalStrength);;
                }

                mCheckingWhatNwk.setText(message);
                mCheckingWhatNwk.setVisibility(View.VISIBLE);

                mCongestionResult.setText(R.string.congestion_results_placeholder);
                mCongestionResult.setVisibility(View.VISIBLE);

                mEmailBodyText = " ---- " + message + " ---- ";
                return;
            }

            if (intent.getAction().equalsIgnoreCase(MeasurementService.BROADCAST_ACTION_LATRUN_PROGRESS)) {
                boolean completedOk = intent.getBooleanExtra(MeasurementService.EXTENDED_DATA_RUNSTATUS, false);
                float packetLoss = intent.getFloatExtra(MeasurementService.EXTENDED_DATA_LAT_PACKETLOSS, 0.0F);
                long clockSkew = intent.getLongExtra(MeasurementService.EXTENDED_DATA_LAT_CLOCKSKEW, 0);
                Log.d(TAG, "Got LAT finished with status: " + String.valueOf(completedOk));
                if (completedOk) {
                    String plPercentageString = new DecimalFormat("#").format(packetLoss*100);
                    String displayResult = "";
                    if (packetLoss <= 0.1F) {
                        displayResult = "Congestion is very low, that's great!";
                    } else if (packetLoss > 0.1F && packetLoss < 0.4F) {
                        displayResult = "Congestion is bad, downloads may be slow";
                    } else {
                        displayResult = "Congestion is terrible. " + plPercentageString + "% of packets lost";
                    }
                    mCongestionResult.setText(displayResult);
                    mEmailBodyText += "\n    * " + displayResult;
                    Toast.makeText(getApplicationContext(), String.format("Packet loss is %s percent", plPercentageString), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "There was a problem measuring congestion :(", Toast.LENGTH_SHORT).show();
                    mCongestionResult.setText("Congestion: failed to measure");
                }
                mCongestionResult.setVisibility(View.VISIBLE);
                mSpeedResult.setVisibility(View.VISIBLE); // Indicate to user to more testing follows.
                return;
            }

            if (intent.getAction().equalsIgnoreCase(MeasurementService.BROADCAST_ACTION_BWRUN_COMPLETED)) {
                boolean completedOk = intent.getBooleanExtra(MeasurementService.EXTENDED_DATA_RUNSTATUS, false);
                String bwMeasurement = intent.getStringExtra(MeasurementService.EXTENDED_DATA_BW_QUAL);
                double bwKbps = intent.getDoubleExtra(MeasurementService.EXTENDED_DATA_BW_KBPS, 0.0);
                double bwMbps = bwKbps/1000.0;
                Log.d(TAG, "Got BW finished with status: " + String.valueOf(completedOk) + ", kbps = " + bwKbps + " / " + bwMeasurement);
                if (completedOk) {
                    Toast.makeText(getApplicationContext(), String.format("Bandwidth measured is %s.",
                            bwMeasurement), Toast.LENGTH_SHORT).show();
                    String prettyMbps = new DecimalFormat("#.#").format(bwMbps); // TODO: Use Mbps for higher speeds
                    String display = "Speed is " + bwMeasurement.toLowerCase() + ", about " + prettyMbps + " Mbps";
                    mEmailBodyText += "\n    * " + display;
                    mSpeedResult.setText(display);
                } else {
                    Toast.makeText(getApplicationContext(), "There was a problem measuring bandwidth :(. Are we online?", Toast.LENGTH_SHORT).show();
                    mSpeedResult.setText("Speed: failed to measure");
                }
                mSpeedResult.setVisibility(View.VISIBLE);
            }

            mEmailButton.setVisibility(View.VISIBLE);

            // Re-enable action button only for COMPLETED case.
            mButtonHowzInternets.setText(R.string.howz_the_internets);
            mButtonHowzInternets.setEnabled(true);

        }
    }
}

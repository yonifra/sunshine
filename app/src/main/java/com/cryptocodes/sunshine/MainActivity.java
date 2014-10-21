package com.cryptocodes.sunshine;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.cryptocodes.sunshine.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import java.io.IOException;
import java.util.List;

public class MainActivity extends ActionBarActivity implements ForecastFragment.Callback,
                                                               GooglePlayServicesClient.ConnectionCallbacks,
                                                               GooglePlayServicesClient.OnConnectionFailedListener,
                                                               LocationListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    public static String CURRENT_GPS_CITY_NAME;

    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds (3 hours)
    public static final int UPDATE_INTERVAL_IN_SECONDS = 10800;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds (1 hour)
    private static final int FASTEST_INTERVAL_IN_SECONDS = 3600;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private static Menu mMenu; // reference to the menu
    private boolean mTwoPane;
    private IntentFilter mCityNameFilter;
    private MainReceiver receiver;
    private LocationClient mLocationClient;
    private Geocoder mGeocoder;

    // Global variable to hold the current location
    Location mCurrentLocation;

    // Define an object that holds accuracy and frequency parameters
    LocationRequest mLocationRequest;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;
    boolean mUpdatesRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();

        // Use balanced accuracy (city block accuracy)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);

        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Open the shared preferences
        mPrefs = getSharedPreferences("SharedPreferences",
                Context.MODE_PRIVATE);
        // Get a SharedPreferences editor
        mEditor = mPrefs.edit();

        mUpdatesRequested = false;

        receiver = new MainReceiver();
        mGeocoder = new Geocoder(this);

        if (findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, new DetailFragment())
                        .commit();
            }

            /*
             * Create a new location client, using the enclosing class to
             * handle callbacks.
             */
            mLocationClient = new LocationClient(this, this, this);
        } else {
            mTwoPane = false;
        }

        ForecastFragment forecastFragment = ((ForecastFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_forecast));

        if (forecastFragment != null) {
            forecastFragment.setUseTodayLayout(!mTwoPane);
        }

        // Create the update city name filter, which will be used to get messages of city name update
        // and notify the action bar accordingly
        mCityNameFilter = new IntentFilter("com.cryptocodes.sunshine.UPDATE_CITY_NAME");

        Log.v(LOG_TAG, "Starting sync from within MainActivity");

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);

        SunshineSyncAdapter.initializeSyncAdapter(this);
        SunshineSyncAdapter.syncImmediately(this);
    }

    @Override
    protected void onResume() {
        /*
         * Get any previous setting for location updates
         * Gets "false" if an error occurs
         */
        if (mPrefs.contains("KEY_UPDATES_ON")) {

            boolean defaultUpdatesState = mPrefs.getBoolean(this.getString(R.string.pref_enable_gps_location), this.getString(R.string.pref_enable_gps_default) == "true");

            mUpdatesRequested = mPrefs.getBoolean("KEY_UPDATES_ON", defaultUpdatesState);

            // Otherwise, turn off location updates
        } else {
            mEditor.putBoolean("KEY_UPDATES_ON", true);
            mEditor.commit();
        }

        super.onResume();
        registerReceiver(receiver, mCityNameFilter);
    }

    @Override
    protected void onPause() {
        // Save the current setting for updates
        mEditor.putBoolean("KEY_UPDATES_ON", mUpdatesRequested);
        mEditor.commit();

        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;

        updateCityName(SunshineSyncAdapter.CURRENT_CITY_NAME);

        return true;
    }

    public void updateCityName(final String cityName) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Sanity check - prevents the app from crashing if this method
                // is run too soon in the app's lifecycle
                if (mMenu == null || cityName == null) return;

                MenuItem item = mMenu.findItem(R.id.action_city_name);

                if (item != null) {
                    item.setTitle(cityName);
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        updateCityName(SunshineSyncAdapter.CURRENT_CITY_NAME);

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(String date) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putString(DetailActivity.DATE_KEY, date);

            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, fragment)
                    .commit();
        } else {
            Intent intent = new Intent(this, DetailActivity.class)
                    .putExtra(DetailActivity.DATE_KEY, date);
            startActivity(intent);
        }
    }

    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        // If already requested, start periodic updates
        if (mUpdatesRequested) {
            mLocationClient.requestLocationUpdates(mLocationRequest, this);
        }

        // Get the current location
        mCurrentLocation = mLocationClient.getLastLocation();

        if (mCurrentLocation != null) {
            try {
                List<Address> resolvedAddresses= mGeocoder.getFromLocation(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), 1);
                if (!resolvedAddresses.isEmpty()) {
                    String cityName = resolvedAddresses.get(0).getLocality().toString();
                    CURRENT_GPS_CITY_NAME = cityName;

                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

                    if (settings.getBoolean(this.getString(R.string.pref_enable_gps_location), true)) {
                        // Update the settings and the sync adapters' location only if GPS is on
                        SharedPreferences.Editor editor1 = settings.edit();
                        editor1.putString(getString(R.string.pref_location_key), cityName);
                        editor1.commit();
                        SunshineSyncAdapter.CURRENT_CITY_NAME = CURRENT_GPS_CITY_NAME;
                        //Toast.makeText(this, "Connected!\nYou're at " + cityName, Toast.LENGTH_LONG).show();
                        Log.i(LOG_TAG, "Connected! Now at " + cityName);
                        updateCityName(cityName);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                //Toast.makeText(this, "Failed to get location", Toast.LENGTH_LONG).show();
                Log.i(LOG_TAG, "Failed to get location. Message: " + e.getMessage());
            }
        } else {
            Toast.makeText(this, getString(R.string.Location_Not_Available), Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
        // Display the connection status
        //Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        Log.i(LOG_TAG, "Location client disconnected, needs to reconnect");
    }

    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    private void showErrorDialog(int errorCode) {
        Toast.makeText(this, "No resolution available, error " + errorCode, Toast.LENGTH_LONG).show();
    }

    /*
     * Handle results returned to the FragmentActivity
     * by Google Play services
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    /*
                     * Try the request again
                     */
                        break;
                }
        }
    }

    /*
     * Called when the Activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();

        // Connect the client.
        mLocationClient.connect();
    }

    /*
     * Called when the Activity is no longer visible.
     */
    @Override
    protected void onStop() {
        // If the client is connected
        if (mLocationClient.isConnected()) {
            /*
             * Remove location updates for a listener.
             * The current Activity is the listener, so
             * the argument is "this".
             */
            mLocationClient.removeLocationUpdates(this);
        }

        // Disconnecting the client invalidates it.
        mLocationClient.disconnect();
        super.onStop();
    }

    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.
                        isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates",
                    "Google Play services is available.");
            // Continue
            return true;
            // Google Play services was not available for some reason.
            // resultCode holds the error code.
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                    resultCode,
                    this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment =
                        new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
                // Show the error dialog in the DialogFragment
                errorFragment.show(getSupportFragmentManager(),
                        "Location Updates");
            }

            return false;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Report to the UI that the location was updated
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public class MainReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String cityName = intent.getStringExtra("city_name");
            updateCityName(cityName);
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
}
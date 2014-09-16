package com.cryptocodes.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.cryptocodes.sunshine.sync.SunshineSyncAdapter;

public class MainActivity extends ActionBarActivity implements ForecastFragment.Callback {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private static Menu mMenu; // reference to the menu
    private boolean mTwoPane;
    private IntentFilter mCityNameFilter;
    private MainReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receiver = new MainReceiver();

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

        SunshineSyncAdapter.initializeSyncAdapter(this);
        SunshineSyncAdapter.syncImmediately(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, mCityNameFilter);
    }

    @Override
    protected void onPause() {
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

    public class MainReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String cityName = intent.getStringExtra("city_name");
            updateCityName(cityName);
        }
    }
}
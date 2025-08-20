package gurux.net.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.TraceEventArgs;
import gurux.net.GXNet;
import gurux.net.android.databinding.ActivityMainBinding;
import gurux.net.android.ui.home.HomeViewModel;
import gurux.net.android.ui.media.MediaViewModel;

public class MainActivity extends AppCompatActivity implements IGXMediaListener {

    private AppBarConfiguration mAppBarConfiguration;
    private GXNet mNet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final HomeViewModel mNetViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        final MediaViewModel mMediaViewModel = new ViewModelProvider(this).get(MediaViewModel.class);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        mNet = new GXNet(this);
        mMediaViewModel.setMedia(mNet);
        readSettings(mNet);
        //Properties are saved after change.
        mNet.addListener(this);
        mNetViewModel.setNet(mNet);
        setContentView(binding.getRoot());

        //PreferenceManager is used to share data between the properties activity and main activity.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if ("mediaSettings".equals(key)) {
                String settings = sharedPreferences.getString("mediaSettings", null);
                if (settings != null) {
                    mNet.removeListener(this);
                    mNet.setSettings(settings);
                    mNet.addListener(this);
                    saveSettings(mNet);
                    //Update UI.
                    mNetViewModel.setNet(mNet);
                }
            }
        });

        setSupportActionBar(binding.appBarMain.toolbar);
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home,
                //Add properties fragment.
                R.id.nav_properties,
                //Add info.
                R.id.nav_info)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_info) {
                try {
                    PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(50, 40, 50, 10);

                    final TextView copyright = new EditText(this);
                    copyright.setMovementMethod(LinkMovementMethod.getInstance());
                    copyright.setText(R.string.copyright);
                    copyright.setTextIsSelectable(false);
                    copyright.setFocusable(false);
                    copyright.setClickable(false);
                    layout.addView(copyright);

                    final TextView version = new EditText(this);
                    version.setMovementMethod(LinkMovementMethod.getInstance());
                    version.setText(String.format("Version: %s", packageInfo.versionName));
                    version.setTextIsSelectable(false);
                    version.setFocusable(false);
                    version.setClickable(false);
                    layout.addView(version);

                    final TextView url = new EditText(this);
                    url.setMovementMethod(LinkMovementMethod.getInstance());
                    url.setText(HtmlCompat.fromHtml("<a href='https://www.gurux.fi'>More info</a>", HtmlCompat.FROM_HTML_MODE_LEGACY));
                    url.setLinksClickable(true);
                    url.setFocusable(false);
                    url.setTextIsSelectable(false);
                    layout.addView(url);
                    new AlertDialog.Builder(this)
                            .setTitle("About Gurux network media")
                            .setView(layout)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                            .show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                drawer.closeDrawer(GravityCompat.START);
                return true;
            }
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) {
                drawer.closeDrawer(GravityCompat.START);
            }
            return handled;
        });
    }

    /**
     * Read settings.
     */
    private void readSettings(GXNet net) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        net.setSettings(sharedPref.getString("settings", null));
    }

    /**
     * Save settings.
     */
    private void saveSettings(GXNet net) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("settings", net.getSettings());
        editor.apply();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            finish();
            return true;
        }
        //Show properties activity.
        if (id == R.id.action_settings) {
            mNet.properties(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onStop() {
        if (mNet != null) {
            mNet.close();
        }
        super.onStop();
    }

    /*
     * Settings activity is disabled when the connection is open.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem settingsItem = menu.findItem(R.id.action_settings);
        if (settingsItem != null) {
            settingsItem.setEnabled(!mNet.isOpen());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onDestroy() {
        if (mNet != null) {
            mNet.close();
        }
        super.onDestroy();
    }

    @Override
    public void onError(Object sender, RuntimeException ex) {

    }

    @Override
    public void onReceived(Object sender, ReceiveEventArgs e) {

    }

    @Override
    public void onMediaStateChange(Object sender, MediaStateEventArgs e) {
    }

    @Override
    public void onTrace(Object sender, TraceEventArgs e) {

    }

    /*
    onPropertyChanged is called when user change settings from the fragment.
     */
    @Override
    public void onPropertyChanged(Object sender, PropertyChangedEventArgs e) {
        if (mNet != null) {
            saveSettings(mNet);
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        }
    }
}
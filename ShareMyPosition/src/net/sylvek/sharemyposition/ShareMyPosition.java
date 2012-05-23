/*
 * Copyright (C) 2010  Sylvain Maucourt (smaucourt@gmail.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 *
 */
package net.sylvek.sharemyposition;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.MapViewMode;
import org.mapsforge.android.maps.Overlay;
import org.mapsforge.android.maps.Projection;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ShareMyPosition extends MapActivity implements LocationListener {

    public static final String EXTRA_INTENT = "extra_intent";

    public static final String LOG = "ShareMyPosition";

    private static final String VERSION = "1.1.2";

    private static final int ZOOM_LEVEL = 17;

    private static final String HOST = "http://sharemyposition.appspot.com/";

    private static final String SHORTY_URI = HOST + "service/create?url=";

    private static final String STATIC_WEB_MAP = HOST + "static.jsp";

    private final static int PROVIDERS_DLG = Menu.FIRST;

    private final static int PROGRESS_DLG = PROVIDERS_DLG + 1;

    private final static int MAP_DLG = PROGRESS_DLG + 1;

    public static final String PREF_LAT_LON_CHECKED = "net.sylvek.sharemyposition.pref.latlon.checked";

    public static final String PREF_ADDRESS_CHECKED = "net.sylvek.sharemyposition.pref.address.checked";

    public static final String PREF_URL_CHECKED = "net.sylvek.sharemyposition.pref.url.checked";

    public static final String PREF_BODY_DEFAULT = "net.sylvek.sharemyposition.pref.body.default";

    private LocationManager locationManager;

    private ConnectivityManager connectivityManager;

    private TelephonyManager telephonyManager;

    private Geocoder gc;

    private final HttpParams params = new BasicHttpParams();

    private WakeLock lock;

    private ToggleButton insideMode;

    private TextView progressText;

    private Location location;

    private MapView sharedMap;

    private SharedPreferences pref;

    private String[] tips;

    private final Random random = new Random();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        HttpProtocolParams.setUserAgent(params, "Android/" + Build.DISPLAY + "/version:" + VERSION);

        gc = new Geocoder(this);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        initWakeLock();

        pref = PreferenceManager.getDefaultSharedPreferences(this);

        tips = getResources().getStringArray(R.array.tips);

        sharedMap = new MapView(ShareMyPosition.this, MapViewMode.MAPNIK_TILE_DOWNLOAD);
        sharedMap.setClickable(true);
        sharedMap.setAlwaysDrawnWithCacheEnabled(true);
        sharedMap.setFocusable(true);
        sharedMap.getOverlays().add(new CenterOverlay(sharedMap));

    }

    private boolean isConnected()
    {
        if (telephonyManager == null || connectivityManager == null) {
            return false;
        }

        final boolean roaming = telephonyManager.isNetworkRoaming();
        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        return info != null && info.isConnected() && !roaming;
    }

    private void displayTip()
    {
        int index = random.nextInt(tips.length);
        String tip = tips[index];
        Log.d(LOG, "generate random tips: " + index + "->" + tip);
        Toast.makeText(ShareMyPosition.this, tip, Toast.LENGTH_LONG).show();
    };

    private void initWakeLock()
    {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        lock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "sharemyposition.lock");
    }

    private void performLocation(boolean forceNetwork)
    {
        locationManager.removeUpdates(ShareMyPosition.this);
        List<String> providers = locationManager.getProviders(true);
        if (providerAvailable(providers)) {
            showDialog(PROGRESS_DLG);

            boolean containsGPS = providers.contains(LocationManager.GPS_PROVIDER);
            boolean containsNetwork = providers.contains(LocationManager.NETWORK_PROVIDER);

            if ((containsGPS && !forceNetwork) || (containsGPS && !containsNetwork)) {
                Log.d(LOG, "gps selected");
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 5, this);
                insideMode.setEnabled(containsNetwork);
                displayTip();
            } else if (containsNetwork) {
                Log.d(LOG, "network selected");
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 5, this);
                insideMode.setEnabled(false);
                displayTip();
            } else {
                Log.w(LOG, "no provided found (GPS or NETWORK)");
                finish();
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        lock.acquire();
        performLocation(false);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        lock.release();
        locationManager.removeUpdates(this);
    }

    private boolean providerAvailable(List<String> providers)
    {
        if (!providers.contains(LocationManager.GPS_PROVIDER) && !providers.contains(LocationManager.NETWORK_PROVIDER)) {
            showDialog(PROVIDERS_DLG);
            return false;
        }

        return true;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog)
    {
        switch (id) {
        default:
            super.onPrepareDialog(id, dialog);
            break;
        case MAP_DLG:
            final View optionsLayout = (View) dialog.findViewById(R.id.custom_layout);
            final FrameLayout map = (FrameLayout) dialog.findViewById(R.id.sharedmap);
            /* to catch dismiss event from AlertControler, we need to "override" onClickListener */
            final Button neutral = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
            neutral.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0)
                {
                    if (optionsLayout.getVisibility() == View.GONE) {
                        optionsLayout.setVisibility(View.VISIBLE);
                        map.setVisibility(View.GONE);
                        neutral.setText(R.string.hide);
                    } else {
                        optionsLayout.setVisibility(View.GONE);
                        map.setVisibility(View.VISIBLE);
                        neutral.setText(R.string.options);
                    }
                }
            });
            if (location != null) {
                sharedMap.getController().setZoom(ZOOM_LEVEL);
                sharedMap.getController().setCenter(new GeoPoint(location.getLatitude(), location.getLongitude()));
            }

            if (!isConnected()) {
                final CheckBox geocodeAddress = (CheckBox) dialog.findViewById(R.id.add_address_location);
                geocodeAddress.setEnabled(false);
                neutral.setEnabled(false);
                map.setVisibility(View.GONE);
                optionsLayout.setVisibility(View.VISIBLE);
            }

            break;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id) {
        default:
            return super.onCreateDialog(id);
        case MAP_DLG:
            final View sharedMapView = LayoutInflater.from(this).inflate(R.layout.sharedmap, null);
            final FrameLayout map = (FrameLayout) sharedMapView.findViewById(R.id.sharedmap);
            map.addView(this.sharedMap);
            final CheckBox latlonAddress = (CheckBox) sharedMapView.findViewById(R.id.add_lat_lon_location);
            final CheckBox geocodeAddress = (CheckBox) sharedMapView.findViewById(R.id.add_address_location);
            final CheckBox urlShortening = (CheckBox) sharedMapView.findViewById(R.id.add_url_location);
            final EditText body = (EditText) sharedMapView.findViewById(R.id.body);

            latlonAddress.setChecked(pref.getBoolean(PREF_LAT_LON_CHECKED, true));
            geocodeAddress.setChecked(pref.getBoolean(PREF_ADDRESS_CHECKED, true));
            urlShortening.setChecked(pref.getBoolean(PREF_URL_CHECKED, true));
            body.setText(pref.getString(PREF_BODY_DEFAULT, getString(R.string.body)));

            return new AlertDialog.Builder(this).setTitle(R.string.app_name)
                    .setView(sharedMapView)
                    .setOnCancelListener(new OnCancelListener() {

                        @Override
                        public void onCancel(DialogInterface arg0)
                        {
                            finish();
                        }
                    })
                    .setNeutralButton(R.string.options, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface arg0, int arg1)
                        {
                            /* needed to display neutral button */
                        }
                    })
                    .setPositiveButton(R.string.share_it, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface arg0, int arg1)
                        {
                            final boolean isLatLong = ((CheckBox) sharedMapView.findViewById(R.id.add_lat_lon_location)).isChecked();
                            final boolean isGeocodeAddress = ((CheckBox) sharedMapView.findViewById(R.id.add_address_location)).isChecked();
                            final boolean isUrlShortening = ((CheckBox) sharedMapView.findViewById(R.id.add_url_location)).isChecked();
                            final EditText body = (EditText) sharedMapView.findViewById(R.id.body);

                            pref.edit()
                                    .putBoolean(PREF_LAT_LON_CHECKED, isLatLong)
                                    .putBoolean(PREF_ADDRESS_CHECKED, isGeocodeAddress)
                                    .putBoolean(PREF_URL_CHECKED, isUrlShortening)
                                    .putString(PREF_BODY_DEFAULT, body.getText().toString())
                                    .commit();

                            Executors.newCachedThreadPool().execute(new Runnable() {

                                @Override
                                public void run()
                                {
                                    GeoPoint p = sharedMap.getMapCenter();
                                    String b = body.getText().toString();
                                    double lat = p.getLatitude();
                                    double lon = p.getLongitude();
                                    String msg = getMessage(lat, lon, b, isGeocodeAddress, isUrlShortening, isLatLong);
                                    Intent t = new Intent(Intent.ACTION_SEND);
                                    t.setType("text/plain");
                                    t.addCategory(Intent.CATEGORY_DEFAULT);
                                    t.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.subject));
                                    t.putExtra(Intent.EXTRA_TEXT, msg);
                                    Intent share = Intent.createChooser(t, getString(R.string.app_name));
                                    startActivity(share);
                                    finish();
                                }

                            });
                        }
                    })
                    .setNegativeButton(R.string.retry, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface arg0, int arg1)
                        {
                            progressText.setText(R.string.progression_desc);
                            insideMode.setChecked(false);
                            performLocation(false);
                        }
                    })
                    .create();
        case PROGRESS_DLG:
            final View progress = LayoutInflater.from(this).inflate(R.layout.progress, null);

            progressText = (TextView) progress.findViewById(R.id.progress);

            insideMode = (ToggleButton) progress.findViewById(R.id.inside_mode);

            insideMode.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    performLocation(isChecked);
                }
            });

            return new AlertDialog.Builder(this).setTitle(getText(R.string.app_name))
                    .setView(progress)
                    .setCancelable(true)
                    .setOnCancelListener(new OnCancelListener() {

                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            finish();
                        }
                    })
                    .create();
        case PROVIDERS_DLG:
            return new AlertDialog.Builder(this).setTitle(R.string.app_name)
                    .setCancelable(false)
                    .setIcon(android.R.drawable.ic_menu_help)
                    .setMessage(R.string.providers_needed)
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            finish();
                        }

                    })
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            Intent gpsProperty = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(gpsProperty);
                        }
                    })
                    .create();
        }
    }

    @Override
    public void onLocationChanged(final Location location)
    {
        Log.d(LOG, "location changed: " + location.toString());
        locationManager.removeUpdates(this);

        this.location = location;

        progressText.setText(R.string.location_changed);

        Log.d(LOG, "stopping tips");

        final Intent extra = getIntent().getParcelableExtra(EXTRA_INTENT);
        if (extra != null) {
            Executors.newCachedThreadPool().execute(new Runnable() {

                @Override
                public void run()
                {
                    Intent b = getIntent();
                    boolean isGeocodeAddress = b.getBooleanExtra(ShareMyPosition.PREF_ADDRESS_CHECKED, true);
                    boolean isLatLong = b.getBooleanExtra(ShareMyPosition.PREF_LAT_LON_CHECKED, true);
                    boolean isUrlShortening = b.getBooleanExtra(ShareMyPosition.PREF_URL_CHECKED, true);
                    String body = b.getStringExtra(ShareMyPosition.PREF_BODY_DEFAULT);
                    String msg = getMessage(location.getLatitude(), location.getLongitude(), body, isGeocodeAddress,
                            isUrlShortening, isLatLong);

                    extra.addCategory(Intent.CATEGORY_DEFAULT);
                    extra.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.subject));
                    extra.putExtra(Intent.EXTRA_TEXT, msg);
                    extra.putExtra("sms_body", msg);
                    startActivity(extra);
                    finish();
                }
            });
        } else {
            showDialog(MAP_DLG);
        }
    }

    private String getMessage(double latitude, double longitude, String body, boolean isGeocodeAddress, boolean isUrlShortening,
            boolean isLatLong)
    {
        final boolean isConnected = isConnected();
        final StringBuilder msg = new StringBuilder(body);
        if (isGeocodeAddress && isConnected) {
            final String address = getAddress(latitude, longitude);
            if (!address.equals("")) {
                if (msg.length() > 0) {
                    msg.append(", ");
                }
                msg.append(address);
            }
        }
        if (isUrlShortening) {
            if (msg.length() > 0) {
                msg.append(", ");
            }
            msg.append(getLocationUrl(isConnected, latitude, longitude));
        }
        if (isLatLong) {
            if (msg.length() > 0) {
                msg.append(", ");
            }
            msg.append(getLatLong(latitude, longitude));
        }

        return msg.toString();
    }

    public String getLocationUrl(boolean isConnected, double latitude, double longitude)
    {
        String url = getCurrentStaticLocationUrl(latitude, longitude);
        if (isConnected) {
            try {
                url = getTinyLink(url);
            } catch (Exception e) {
                Log.e(LOG, "tinyLink don't work: " + url);
            }
        }
        return url;
    }

    public String getCurrentStaticLocationUrl(double latitude, double longitude)
    {
        StringBuilder uri = new StringBuilder(STATIC_WEB_MAP).append("?pos=").append(latitude).append(",").append(longitude);
        return uri.toString();
    }

    public String getAddress(double latitude, double longitude)
    {
        List<Address> address = null;
        try {
            address = gc.getFromLocation(latitude, longitude, 1);
        } catch (IOException e) {
            Log.e(LOG, "unable to get address", e);
            return "";
        }

        if (address == null || address.size() == 0) {
            Log.w(LOG, "unable to parse address");
            return "";
        }

        Address a = address.get(0);

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.getMaxAddressLineIndex(); i++) {
            b.append(a.getAddressLine(i));
            if (i < (a.getMaxAddressLineIndex() - 1)) {
                b.append(" ");
            }
        }

        return b.toString();
    }

    public String getLatLong(double latitude, double longitude)
    {
        return "(pos=" + latitude + "," + longitude + ")";
    }

    public String getTinyLink(String url) throws ClientProtocolException, IOException, JSONException
    {
        HttpClient client = new DefaultHttpClient(params);
        HttpGet get = new HttpGet(SHORTY_URI + URLEncoder.encode(url));
        HttpResponse response = client.execute(get);
        if (response.getStatusLine().getStatusCode() == 200) {
            return EntityUtils.toString(response.getEntity());
        }

        return url;
    }

    @Override
    public void onProviderDisabled(String provider)
    {
        performLocation(false);
    }

    @Override
    public void onProviderEnabled(String provider)
    {
        performLocation(false);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        // do nothing here
    }

    public class CenterOverlay extends Overlay {

        private final MapView map;

        private Bitmap pin;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                this.setAlpha(255);
                this.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
            }
        };

        private Point out;

        /**
         * @param fillPaint
         * @param outlinePaint
         */
        public CenterOverlay(MapView map)
        {
            super();
            this.map = map;

            BitmapDrawable b = (BitmapDrawable) getResources().getDrawable(R.drawable.pin);
            this.pin = b.getBitmap();
        }

        @Override
        protected void drawOverlayBitmap(Canvas canvas, Point drawPosition, Projection projection, byte drawZoomLevel)
        {
            if (map != null) {
                final GeoPoint in = map.getMapCenter();
                if (in != null) {
                    out = projection.toPoint(in, out, drawZoomLevel);
                    float x = out.x - drawPosition.x - pin.getWidth() / 2;
                    float y = out.y - drawPosition.y - pin.getHeight();
                    canvas.drawBitmap(pin, x, y, paint);
                }
            }
        }
    }

}
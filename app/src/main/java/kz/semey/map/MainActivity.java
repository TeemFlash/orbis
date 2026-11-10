package kz.semey.map;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.*;
import kz.semey.map.motion.GlobeTiltController;

/** Lab 9 A. Foreground device location and selectable OpenStreetMap landmarks. */
public class MainActivity extends Activity implements LocationListener {
  private static final int LIME = 0xFFB7E7DB, INK = 0xFF181B19, REQUEST = 9;
  private boolean active, ready, centerOnFix = true;
  private MapView view;
  private FrameLayout root;
  private android.webkit.WebView globe;
  private boolean globeVisible = true, globeLoaded;
  private boolean globeTiltEnabled = true;
  private GlobeTiltController tiltController;
  private View placeCard;
  private Button globeTab, streetTab;
  private MapLibreMap map;
  private LocationManager manager;
  private Location location;
  private Marker user;
  private TextView status, title, description, distance, category, city;
  private final ExecutorService geocoding = Executors.newSingleThreadExecutor();
  private Location lastCityQuery;
  private long lastCityQueryTime;
  private int cityRequest;
  private boolean cityResolved, showingLocation = true;
  private String cityName = "Your location";
  private int selected;
  private final HashMap<Long, Integer> indices = new HashMap<>();
  private final ArrayList<Marker> markers = new ArrayList<>();
  private final String[] names = {"Stronger Than Death", "Alash–Auezov Museum", "Yamyshevsky Gate"};
  private final String[] types = {
    "MEMORIAL · PEACE ISLAND", "MUSEUM · SEMEY", "HISTORIC LANDMARK · SEMEY"
  };
  private final String[] infos = {
    "A memorial to the victims of nuclear testing at the Semipalatinsk Test Site.",
    "A historic house associated with Abai and the literary heritage of the Alash movement.",
    "The surviving stone gate of the old Semipalatinsk fortress, dating to 1776."
  };
  private final LatLng[] points = {
    new LatLng(50.39263, 80.26233), new LatLng(50.40702, 80.25629), new LatLng(50.40933, 80.23904)
  };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    FirebaseConfig.init();
    AppUi.bars(this, false);
    MapLibre.getInstance(this);
    if (state != null) globeVisible = state.getBoolean("globe", true);
    manager = (LocationManager) getSystemService(LOCATION_SERVICE);
    tiltController = new GlobeTiltController(this, this::sendTiltToGlobe);
    if (state != null) selected = state.getInt("selected", 0);
    root = new FrameLayout(this);
    root.setBackgroundColor(0xFFF3F5F0);
    setContentView(root);
    view = new MapView(this);
    root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    view.onCreate(state);
    globe = new android.webkit.WebView(this);
    globe.setBackgroundColor(0xFFAFE3F8);
    globe.getSettings().setJavaScriptEnabled(true);
    globe.getSettings().setBlockNetworkLoads(true);
    globe.getSettings().setAllowContentAccess(false);
    globe.getSettings().setAllowFileAccess(false);
    globe.setWebViewClient(
        new android.webkit.WebViewClient() {
          @Override
          public void onPageFinished(android.webkit.WebView v, String url) {
            globeLoaded = true;
            updateGlobe();
            setMapMode(globeVisible);
          }
        });
    root.addView(globe, new FrameLayout.LayoutParams(-1, -1));
    try (java.io.InputStream in = getAssets().open("globe.html")) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
      globe.loadDataWithBaseURL(
          "https://appassets.androidplatform.net/",
          bytes.toString("UTF-8"),
          "text/html",
          "UTF-8",
          null);
    } catch (java.io.IOException e) {
      globeVisible = false;
    }

    LinearLayout heading = new LinearLayout(this);
    heading.setOrientation(LinearLayout.VERTICAL);
    heading.setPadding(dp(20), dp(16), dp(20), dp(15));
    android.graphics.drawable.GradientDrawable headingBackground = AppUi.wash();
    headingBackground.setCornerRadius(dp(26));
    heading.setBackground(headingBackground);
    city = text(cityName, 30, INK);
    city.setMaxLines(2);
    city.setEllipsize(android.text.TextUtils.TruncateAt.END);
    city.setTypeface(null, Typeface.BOLD);
    heading.addView(city);
    status = text("Loading map…", 12, 0xFF626760);
    heading.addView(status);
    status.setOnClickListener(
        v -> {
          if (!ready) load();
          else locate();
        });
    FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    hp.setMargins(dp(20), dp(16), dp(20), 0);
    LinearLayout modes = new LinearLayout(this);
    globeTab = AppUi.button(this, "Globe", Color.WHITE, AppUi.INK);
    streetTab = AppUi.button(this, "Map", Color.TRANSPARENT, AppUi.INK);
    globeTab.setOnClickListener(v -> setMapMode(true));
    streetTab.setOnClickListener(v -> setMapMode(false));
    modes.addView(globeTab, new LinearLayout.LayoutParams(0, dp(44), 1));
    modes.addView(streetTab, new LinearLayout.LayoutParams(0, dp(44), 1));
    heading.addView(modes, AppUi.row(this, 12));
    root.addView(heading, hp);
    LinearLayout bottom = new LinearLayout(this);
    bottom.setOrientation(LinearLayout.VERTICAL);
    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    android.widget.ImageButton gps =
        AppUi.iconButton(this, R.drawable.ic_locate_fixed, "Find my location", LIME);
    gps.setContentDescription("Find my location");
    gps.setOnClickListener(v -> locate());
    LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(48), dp(48));
    controls.addView(gps, gp);
    bottom.addView(controls);
    LinearLayout card = new LinearLayout(this);
    placeCard = card;
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(22), dp(20), dp(22), dp(19));
    card.setBackground(shape(Color.WHITE, 26));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
    cp.topMargin = dp(12);
    bottom.addView(card, cp);
    category = text("", 10, 0xFF63705B);
    category.setLetterSpacing(.08f);
    card.addView(category);
    title = text("", 22, INK);
    title.setTypeface(null, Typeface.BOLD);
    title.setPadding(0, dp(6), 0, dp(6));
    card.addView(title);
    description = text("", 14, 0xFF626760);
    description.setLineSpacing(dp(2), 1);
    card.addView(description);
    distance = text("", 12, 0xFF536348);
    distance.setPadding(0, dp(12), 0, 0);
    card.addView(distance);
    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    bp.setMargins(dp(20), 0, dp(20), dp(100));
    root.addView(bottom, bp);
    addNavigation();
    updateCard();
    setMapMode(globeVisible);
    view.addOnDidFailLoadingMapListener(
        error -> {
          ready = false;
          status.setText("Map unavailable · tap to retry");
        });
    view.getMapAsync(
        m -> {
          map = m;
          map.getUiSettings().setCompassEnabled(false);
          map.getUiSettings().setLogoEnabled(false);
          map.getUiSettings().setAttributionMargins(dp(20), 0, 0, dp(8));
          map.setOnMarkerClickListener(
              marker -> {
                Integer i = indices.get(marker.getId());
                if (i != null) {
                  select(i);
                  return true;
                }
                if (marker == user) {
                  showingLocation = true;
                  updateCard();
                  return true;
                }
                return false;
              });
          load();
          if (location != null) centerUser();
          else overview();
        });
    if (state == null && !allowed())
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          REQUEST);
  }

  private void addNavigation() {
    View nav = AppNavigation.create(this, "map", null);
    FrameLayout.LayoutParams navLayout = new FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM);
    navLayout.setMargins(dp(20), 0, dp(20), dp(32));
    root.addView(nav, navLayout);
  }

  private void setMapMode(boolean showGlobe) {
    status.setVisibility(showGlobe ? View.GONE : View.VISIBLE);
    globeVisible = showGlobe;
    globe.setVisibility(showGlobe ? View.VISIBLE : View.GONE);
    view.setVisibility(showGlobe ? View.GONE : View.VISIBLE);
    globeTab.setBackground(AppUi.round(this, showGlobe ? Color.WHITE : Color.TRANSPARENT, 22));
    streetTab.setBackground(AppUi.round(this, !showGlobe ? Color.WHITE : Color.TRANSPARENT, 22));
    if (globeLoaded)
      globe.evaluateJavascript("window.setRunning(" + (showGlobe && active) + ")", null);
    syncTiltMonitor();
    updateCard();
  }

  private void syncTiltMonitor() {
    if (tiltController == null) return;
    if (active && globeVisible && globeTiltEnabled) tiltController.start();
    else tiltController.stop();
  }

  private void sendTiltToGlobe(float horizontal, float vertical) {
    if (!globeLoaded || !active || !globeVisible) return;
    globe.evaluateJavascript(
        String.format(Locale.US, "window.setDeviceTilt(%.6f,%.6f)", horizontal, vertical), null);
  }

  private void updateGlobe() {
    if (globeLoaded && location != null)
      globe.evaluateJavascript(
          String.format(
              Locale.US,
              "window.setPoint(%.6f,%.6f)",
              location.getLatitude(),
              location.getLongitude()),
          null);
  }

  private void load() {
    if (map == null) return;
    status.setText("Loading map…");
    map.setStyle(
        new Style.Builder().fromUri("asset://map-light.json"),
        style -> {
          ready = true;
          map.clear();
          indices.clear();
          markers.clear();
          user = null;
          for (int i = 0; i < points.length; i++) {
            Marker m =
                map.addMarker(
                    new MarkerOptions()
                        .position(points[i])
                        .icon(dot(i == selected ? 0xFF6B922F : 0xFF4C725C, i == selected)));
            markers.add(m);
            indices.put(m.getId(), i);
          }
          drawUser();
          updateStatus();
        });
  }

  private Icon dot(int color, boolean large) {
    Bitmap b = Bitmap.createBitmap(dp(46), dp(46), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(3);
    float mid = dp(23);
    p.setColor(color);
    p.setAlpha(35);
    c.drawCircle(mid, mid, dp(large ? 21 : 16), p);
    p.setAlpha(70);
    c.drawCircle(mid, mid, dp(large ? 14 : 11), p);
    p.setAlpha(255);
    c.drawCircle(mid, mid, dp(large ? 7 : 5), p);
    return IconFactory.getInstance(this).fromBitmap(b);
  }

  private void select(int i) {
    showingLocation = false;
    selected = i;
    updateCard();
    for (int j = 0; j < markers.size(); j++)
      markers.get(j).setIcon(dot(j == selected ? 0xFF6B922F : 0xFF4C725C, j == selected));
    if (map != null) map.animateCamera(CameraUpdateFactory.newLatLng(points[i]));
  }

  private void overview() {
    if (map != null)
      map.animateCamera(
          CameraUpdateFactory.newLatLngBounds(
              new LatLngBounds.Builder().includes(Arrays.asList(points)).build(),
              dp(45),
              dp(155),
              dp(45),
              dp(340)));
  }

  private void updateCard() {
    placeCard.setVisibility(!showingLocation && !globeVisible ? View.VISIBLE : View.GONE);
    if (showingLocation) return;
    category.setText(types[selected]);
    title.setText(names[selected]);
    description.setText(infos[selected]);
    if (location == null) distance.setText("Tap the location button to see your distance");
    else {
      float[] r = new float[1];
      Location.distanceBetween(
          location.getLatitude(),
          location.getLongitude(),
          points[selected].getLatitude(),
          points[selected].getLongitude(),
          r);
      distance.setText(
          (r[0] < 1000
                  ? String.format(Locale.US, "%.0f m", r[0])
                  : String.format(Locale.US, "%.1f km", r[0] / 1000))
              + " away · straight-line distance");
    }
  }

  private boolean locationEnabled() {
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
  }

  private boolean allowed() {
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void locate() {
    if (globeVisible && globeLoaded) globe.evaluateJavascript("window.focusPoint()", null);
    showingLocation = true;
    updateCard();
    centerOnFix = true;
    if (!allowed()) {
      if (getPreferences(0).getBoolean("asked", false)
          && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
          && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
        new AlertDialog.Builder(this)
            .setTitle("Location permission")
            .setMessage(
                "Allow location in app settings to show your position. You can still explore Semey"
                    + " without it.")
            .setPositiveButton(
                "Open settings",
                (d, w) ->
                    startActivity(
                        new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Not now", null)
            .show();
      else
        requestPermissions(
            new String[] {
              Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            },
            REQUEST);
      return;
    }
    if (!locationEnabled()) {
      new AlertDialog.Builder(this)
          .setTitle("Turn on location")
          .setMessage("Enable device location to find your position.")
          .setPositiveButton(
              "Open settings",
              (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
          .setNegativeButton("Not now", null)
          .show();
      return;
    }
    startLocation();
    if (location != null && fresh(location)) centerUser();
  }

  private boolean fresh(Location l) {
    return android.os.SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()
        < 120_000_000_000L;
  }

  private void startLocation() {
    if (!active || !allowed()) return;
    try {
      manager.removeUpdates(this);
      boolean enabled = false;
      for (String provider :
          new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER})
        if (manager.isProviderEnabled(provider)) {
          if (provider.equals(LocationManager.GPS_PROVIDER)
              && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                  != PackageManager.PERMISSION_GRANTED) continue;
          manager.requestLocationUpdates(provider, 3000, 3, this);
          enabled = true;
          Location last = manager.getLastKnownLocation(provider);
          if (last != null
              && fresh(last)
              && (location == null
                  || last.getElapsedRealtimeNanos() > location.getElapsedRealtimeNanos()))
            onLocationChanged(last);
        }
      if (!enabled) status.setText("Location unavailable · tap the location button");
      else updateStatus();
    } catch (SecurityException e) {
      status.setText("Location permission needed");
    }
  }

  private void updateStatus() {
    if (!ready) return;
    status.setVisibility(globeVisible ? View.GONE : View.VISIBLE);
    if (!allowed()) status.setText("location permission needed");
    else if (!locationEnabled()) status.setText("device location is off");
    else if (location == null) status.setText("waiting for location");
    else {
      status.setText("");
      status.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLocationChanged(Location l) {
    location = l;
    updateGlobe();
    resolveCity(l);
    drawUser();
    updateCard();
    updateStatus();
    if (centerOnFix) centerUser();
  }

  /**
   * Reverse geocoding runs off the UI thread; GPS updates are throttled to avoid repeated requests.
   */
  private void resolveCity(Location fix) {
    long now = android.os.SystemClock.elapsedRealtime();
    if (lastCityQuery != null
        && lastCityQuery.distanceTo(fix) < 1000
        && now - lastCityQueryTime < (cityResolved ? 300000 : 60000)) return;
    lastCityQuery = new Location(fix);
    lastCityQueryTime = now;
    final int request = ++cityRequest;
    cityResolved = false;
    cityName = "Your location";
    city.setText(cityName);
    if (!Geocoder.isPresent()) {
      updateCard();
      return;
    }
    final double latitude = fix.getLatitude(), longitude = fix.getLongitude();
    geocoding.execute(
        () -> {
          String result = null;
          try {
            List<Address> addresses =
                new Geocoder(getApplicationContext(), Locale.ENGLISH)
                    .getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
              Address address = addresses.get(0);
              result = address.getLocality();
              if (result == null || result.trim().isEmpty()) result = address.getSubAdminArea();
              if (result == null || result.trim().isEmpty()) result = address.getAdminArea();
            }
          } catch (java.io.IOException | IllegalArgumentException ignored) {
            // Keep a neutral title if the service is offline or cannot identify this area.
          }
          final String resolved = result;
          runOnUiThread(
              () -> {
                if (isDestroyed() || request != cityRequest) return;
                cityResolved = resolved != null && !resolved.trim().isEmpty();
                cityName = cityResolved ? resolved : "Your location";
                city.setText(cityName);
                updateCard();
              });
        });
  }

  private void drawUser() {
    if (!ready || map == null || location == null) return;
    LatLng p = new LatLng(location.getLatitude(), location.getLongitude());
    if (user == null)
      user = map.addMarker(new MarkerOptions().position(p).icon(dot(0xFF247AC4, true)));
    else user.setPosition(p);
  }

  private void centerUser() {
    if (map != null && location != null) {
      map.animateCamera(
          CameraUpdateFactory.newLatLngZoom(
              new LatLng(location.getLatitude(), location.getLongitude()), 15));
      centerOnFix = false;
    }
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(code, permissions, grants);
    if (code == REQUEST) {
      getPreferences(0).edit().putBoolean("asked", true).apply();
      if (allowed()) startLocation();
      updateStatus();
    }
  }

  @Override
  public void onProviderEnabled(String p) {
    startLocation();
  }

  @Override
  public void onProviderDisabled(String p) {
    updateStatus();
  }

  @Override
  public void onStatusChanged(String p, int s, Bundle b) {}

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int c, int r) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c);
    d.setCornerRadius(dp(r));
    return d;
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private TextView button(String s, boolean accent) {
    TextView t = text(s, 14, INK);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(17), 0, dp(17), 0);
    t.setBackground(shape(accent ? LIME : Color.WHITE, 24));
    return t;
  }

  @Override
  protected void onStart() {
    super.onStart();
    view.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    view.onResume();
    active = true;
    globeTiltEnabled = LocalSettings.isGlobeTiltEnabled(this);
    globe.onResume();
    setMapMode(globeVisible);
    startLocation();
    updateStatus();
  }

  @Override
  protected void onPause() {
    active = false;
    syncTiltMonitor();
    if (globeLoaded) globe.evaluateJavascript("window.setRunning(false)", null);
    globe.onPause();
    manager.removeUpdates(this);
    view.onPause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    view.onStop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    cityRequest++;
    if (tiltController != null) tiltController.stop();
    globe.destroy();
    geocoding.shutdownNow();
    view.onDestroy();
    super.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    view.onLowMemory();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putInt("selected", selected);
    b.putBoolean("globe", globeVisible);
    view.onSaveInstanceState(b);
  }
}

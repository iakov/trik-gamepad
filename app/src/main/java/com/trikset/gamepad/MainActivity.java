package com.trikset.gamepad;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.demo.mjpeg.MjpegView;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
  static final String TAG = "MainActivity";
  private HideRunnable mHideRunnable;
  private SensorManager mSensorManager;
  private int mAngle; // -100%
  // ...
  // +100%
  private boolean mWheelEnabled;
  private SenderService mSender;
  private int mWheelStep = 7;
  @Nullable private MjpegView mVideo;
  @Nullable private URL mVideoURL;
  @Nullable private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferencesListener;

  // @SuppressWarnings("deprecation")
  // @TargetApi(16)
  private void createPad(int id, String strId) {
    final SquareTouchPadLayout pad = findViewById(id);
    if (pad != null) {
      pad.setPadName("pad " + strId);
      pad.setSender(getSenderService());
    }
    // if (android.os.Build.VERSION.SDK_INT >= 16) {
    // pad.setBackground(image);
    // } else {
    // pad.setBackgroundDrawable(image);
    // }
  }

  @Override
  public void onAccuracyChanged(final Sensor arg0, final int arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    // Edge-to-edge (required on API 35+; enforced for targetSdk 36): draw
    // behind the system bars instead of using the removed FLAG_FULLSCREEN.
    // The gamepad UI hides the bars via setSystemUiVisibility(false).
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setHideRunnable(new HideRunnable());
    setSystemUiVisibility(false);
    {
      ActionBar a = getSupportActionBar();
      if (a != null) {

        a.setDisplayShowHomeEnabled(true);
        a.setDisplayUseLogoEnabled(false);
        a.setLogo(R.drawable.trik_gamepad_logo_512x512);

        a.setDisplayShowTitleEnabled(true);
      }
    }

    setSenderService(new SenderService());
    mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

    mVideo = findViewById(R.id.video);

    recreateMagicButtons(5);

    {
      getSenderService().setOnDisconnectedListener(reason -> toast("Disconnected." + reason));
      getSenderService().setShowTextCallback(this::toast);
    }

    {
      final Button btnSettings = findViewById(R.id.btnSettings);
      if (btnSettings != null) {
        btnSettings.setOnClickListener(
            v -> {
              ActionBar a = getSupportActionBar();
              if (a != null) {
                setSystemUiVisibility(!a.isShowing());
              }
            });
      }
    }

    {
      final View controlsOverlay = findViewById(R.id.controlsOverlay);
      if (controlsOverlay != null) {
        controlsOverlay.bringToFront();
      }
    }

    {
      createPad(R.id.leftPad, "1");
      createPad(R.id.rightPad, "2");
    }

    {
      final SharedPreferences prefs =
          PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      mSharedPreferencesListener =
          new SharedPreferences.OnSharedPreferenceChangeListener() {
            private float mPrevAlpha;

            @Override
            public void onSharedPreferenceChanged(
                @NonNull final SharedPreferences sharedPreferences, final String key) {
              final String addr =
                  sharedPreferences.getString(SettingsFragment.SK_HOST_ADDRESS, "192.168.77.1");
              int portNumber = 4444;
              final String portStr =
                  sharedPreferences.getString(SettingsFragment.SK_HOST_PORT, "4444");
              try {
                portNumber = Integer.parseInt(portStr);
              } catch (@NonNull final NumberFormatException e) {
                toast("Port number '" + portStr + "' is incorrect.");
              }
              final String oldAddr = getSenderService().getHostAddr();
              getSenderService().setTarget(addr, portNumber);

              {
                ActionBar a = getSupportActionBar();

                if (a != null) {
                  a.setTitle(addr);
                } else {
                  toast("Can not change title, not a problem");
                }
              }

              if (!addr.equalsIgnoreCase(oldAddr)) {

                // update video stream URI when target addr changed
                sharedPreferences
                    .edit()
                    .putString(
                        SettingsFragment.SK_VIDEO_URI,
                        "http://" + addr.trim() + ":8080/?action=stream")
                    .apply();
              }

              {
                final int defAlpha = 100;
                int padsAlpha = defAlpha;

                try {
                  padsAlpha =
                      Integer.parseInt(
                          sharedPreferences.getString(
                              SettingsFragment.SK_SHOW_PADS, String.valueOf(defAlpha)));
                } catch (NumberFormatException nfe) {
                  // unchanged
                }

                final float alpha = Math.max(0, Math.min(255, padsAlpha)) / 255.0f;
                AlphaAnimation alphaUp = new AlphaAnimation(mPrevAlpha, alpha);
                mPrevAlpha = alpha;
                alphaUp.setFillAfter(true);
                alphaUp.setDuration(2000);
                final View co = findViewById(R.id.controlsOverlay);
                if (co != null) {
                  co.startAnimation(alphaUp);
                }
                final View btns = findViewById(R.id.buttons);
                if (btns != null) {
                  btns.startAnimation(alphaUp);
                }
              }

              {
                // "http://trackfield.webcam.oregonstate.edu/axis-cgi/mjpg/video.cgi?resolution=320x240";

                String videoStreamURI =
                    sharedPreferences.getString(
                        SettingsFragment.SK_VIDEO_URI, "http://" + addr + ":8080/?action=stream");

                // --no-sout-audio --sout
                // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:"
                // "rtp{ttl=5,sdp=rtsp://:8889/s}"
                // works only with vcodec=mp4v without audio :(

                // http://developer.android.com/reference/android/media/MediaPlayer.html
                // http://developer.android.com/guide/appendix/media-formats.html

                try {
                  mVideoURL = videoStreamURI.isEmpty() ? null : new URI(videoStreamURI).toURL();
                } catch (URISyntaxException | MalformedURLException e) {
                  toast("Illegal video stream URL");
                  Log.e(TAG, "onSharedPreferenceChanged: ", e);
                  mVideoURL = null;
                }
              }

              {
                mWheelStep =
                    Objects.requireNonNull(
                        Integer.getInteger(
                            sharedPreferences.getString(
                                SettingsFragment.SK_WHEEL_STEP, String.valueOf(mWheelStep)),
                            mWheelStep));
                mWheelStep = Math.max(1, Math.min(100, mWheelStep));
              }

              {
                try {
                  final int timeout =
                      Integer.parseInt(
                          sharedPreferences.getString(
                              SettingsFragment.SK_KEEPALIVE,
                              Integer.toString(SenderService.DEFAULT_KEEPALIVE)));
                  if (timeout < SenderService.MINIMAL_KEEPALIVE) {
                    toast(
                        String.format(
                            Locale.US,
                            getString(R.string.keepalive_must_be_not_less),
                            SenderService.MINIMAL_KEEPALIVE));

                    sharedPreferences
                        .edit()
                        .putString(
                            SettingsFragment.SK_KEEPALIVE,
                            Integer.toString(getSenderService().getKeepaliveTimeout()))
                        .apply();
                  } else {
                    getSenderService().setKeepaliveTimeout(timeout);
                  }
                } catch (NumberFormatException e) {
                  toast(getString(R.string.keepalive_must_be_positive_decimal));

                  sharedPreferences
                      .edit()
                      .putString(
                          SettingsFragment.SK_KEEPALIVE,
                          Integer.toString(getSenderService().getKeepaliveTimeout()))
                      .apply();
                }
              }
            }
          };
      mSharedPreferencesListener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_ADDRESS);
      prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(@NonNull Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu, menu);

    // TODO: remove this hack
    final CheckBox w = (CheckBox) MenuItemCompat.getActionView(menu.findItem(R.id.wheel));
    w.setText(getResources().getString(R.string.menu_wheel));

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.settings) {
      final Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
      startActivity(settings);
      return true;
    } else if (item.getItemId() == R.id.wheel) {
      mWheelEnabled = !mWheelEnabled;
      item.setChecked(mWheelEnabled);
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onPause() {
    mSensorManager.unregisterListener(this);
    getSenderService().disconnect("Inactive gamepad");
    if (mVideo != null) {
      mVideo.stopPlayback();
      mVideo.setOnStreamErrorListener(null);
    }
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (mVideo != null) {
      // Reconnect-on-error: the render thread reports a dead stream and we
      // drop the HTTP connection and restart it. No forced periodic restart —
      // the stream only restarts when it actually breaks (see .PLAN.md R12).
      mVideo.setOnStreamErrorListener(this::restartVideoStream);
      restartVideoStream();
    }

    mSensorManager.registerListener(
        this, mSensorManager.getDefaultSensor(Sensor.TYPE_ALL), SensorManager.SENSOR_DELAY_NORMAL);
  }

  private void restartVideoStream() {
    // The error listener may fire from the render thread; always hop to the
    // main thread before touching the view hierarchy / launching an AsyncTask.
    runOnUiThread(
        () -> {
          if (mVideo == null) {
            return;
          }
          new StartReadMjpegAsync(mVideo).execute(mVideoURL);
        });
  }

  @Override
  public void onSensorChanged(@NonNull final SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      if (!mWheelEnabled) {
        return;
      }
      processSensor(event.values);
    } else {
      Log.i("Sensor", String.valueOf(event.sensor.getType()));
    }
  }

  private void processSensor(final float[] values) {
    final double WHEEL_BOOSTER_MULTIPLIER = 1.5;
    final double x = values[0];
    final double y = values[1];
    if (x < 1e-6) {
      return;
    }

    int angle = (int) (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y, x) / Math.PI);

    if (Math.abs(angle) < 10) {
      angle = 0;
    } else if (angle > 100) {
      angle = 100;
    } else if (angle < -100) {
      angle = -100;
    }

    if (Math.abs(mAngle - angle) < mWheelStep) {
      return;
    }

    mAngle = angle;

    getSenderService().send("wheel " + mAngle);
  }

  private void recreateMagicButtons(final int count) {
    final ViewGroup buttonsView = findViewById(R.id.buttons);
    if (buttonsView == null) {
      return;
    }
    buttonsView.removeAllViews();
    for (int num = 1; num <= count; ++num) {
      final Button btn = new Button(MainActivity.this);
      btn.setHapticFeedbackEnabled(true);
      btn.setGravity(Gravity.CENTER);
      btn.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      final String name = String.valueOf(num);
      btn.setText(name);
      btn.setBackgroundResource(R.drawable.button_shape);

      btn.setOnClickListener(
          arg0 -> {
            SenderService sender = getSenderService();
            if (sender != null) {
              sender.send("btn " + name + " down"); // TODO: "up" via
              // TouchListner
              btn.performHapticFeedback(
                  HapticFeedbackConstants.LONG_PRESS,
                  HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
          });
      buttonsView.addView(btn);
    }
  }

  // Hides/shows the system bars. Uses the modern WindowInsetsControllerCompat
  // (available back to API 21 via core-ktx) instead of the deprecated
  // View.SYSTEM_UI_FLAG_* set, which leaves the window without focus on API 36
  // where edge-to-edge is enforced.
  private void setSystemUiVisibility(boolean show) {
    final View mainView = findViewById(R.id.main);
    if (mainView == null) {
      return;
    }

    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(getWindow(), mainView);
    if (controller == null) {
      return;
    }

    if (show) {
      controller.show(WindowInsetsCompat.Type.systemBars());
      // The action bar is only shown while the settings overlay is toggling.
      ActionBar a = getSupportActionBar();
      if (a != null) {
        a.show();
      }
    } else {
      controller.hide(WindowInsetsCompat.Type.systemBars());
      controller.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    HideRunnable r = getHideRunnable();
    if (r != null) {
      mainView.removeCallbacks(r);
      mainView.postDelayed(r, 3000);
    }
  }

  private void toast(final String text) {
    runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show());
  }

  public SenderService getSenderService() {
    return mSender;
  }

  public void setSenderService(SenderService sender) {
    this.mSender = sender;
  }

  private HideRunnable getHideRunnable() {
    return mHideRunnable;
  }

  private void setHideRunnable(HideRunnable r) {
    this.mHideRunnable = r;
  }

  private class HideRunnable implements Runnable {

    @Override
    public void run() {
      setSystemUiVisibility(false);
    }
  }

  @Override
  protected void onDestroy() {
    mSensorManager.unregisterListener(this);

    if (mVideo != null) {
      mVideo.stopPlayback();
      mVideo.setOnStreamErrorListener(null);
      mVideo = null;
    }
    final View mainView = findViewById(R.id.main);
    mainView.removeCallbacks(getHideRunnable());
    final ViewGroup buttonsView = findViewById(R.id.buttons);
    if (buttonsView != null) {
      for (int i = 0; i < buttonsView.getChildCount(); ++i) {
        buttonsView.getChildAt(i).setOnClickListener(null);
      }
    }

    final Button btnSettings = findViewById(R.id.btnSettings);
    if (btnSettings != null) {
      btnSettings.setOnClickListener(null);
    }

    final SquareTouchPadLayout pad1 = findViewById(R.id.leftPad);
    if (pad1 != null) {
      pad1.setSender(null);
    }

    final SquareTouchPadLayout pad2 = findViewById(R.id.rightPad);
    if (pad2 != null) {
      pad2.setSender(null);
    }

    PreferenceManager.getDefaultSharedPreferences(getBaseContext())
        .unregisterOnSharedPreferenceChangeListener(mSharedPreferencesListener);
    getSenderService().setOnDisconnectedListener(null);
    getSenderService().setShowTextCallback(null);
    setSenderService(null);
    setHideRunnable(null);
    super.onDestroy();
  }
}

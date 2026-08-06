package com.demo.mjpeg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class MjpegViewTest {

  @Test
  public void setSourceAndStartStopPlaybackShouldNotCrash() {
    MjpegView view = new MjpegView(RuntimeEnvironment.getApplication());
    view.setSource(new MjpegInputStream(new ByteArrayInputStream(new byte[0])));
    // With an empty stream the render loop hits EOF quickly; stop it.
    view.startPlayback();
    view.stopPlayback();
  }

  @Test
  public void stopPlaybackWhenNotRunningShouldBeNoOp() {
    MjpegView view = new MjpegView(RuntimeEnvironment.getApplication());
    view.stopPlayback();
  }

  @Test
  public void setSourceNullThenStartPlaybackShouldBeNoOp() {
    MjpegView view = new MjpegView(RuntimeEnvironment.getApplication());
    view.setSource(null);
    view.startPlayback();
  }

  @Test
  public void surfaceCallbacksShouldNotThrow() {
    MjpegView view = new MjpegView(RuntimeEnvironment.getApplication());
    android.view.SurfaceHolder holder = view.getHolder();
    view.surfaceCreated(holder);
    view.surfaceChanged(holder, 0, 640, 480);
    // surfaceDestroyed stops playback; safe.
    view.surfaceDestroyed(holder);
  }

  @Test
  public void streamErrorListenerShouldBeSettable() {
    MjpegView view = new MjpegView(RuntimeEnvironment.getApplication());
    view.setOnStreamErrorListener(() -> {});
    view.setOnStreamErrorListener(null);
  }

  @Test
  public void constructorsShouldCreateView() {
    MjpegView viaContext = new MjpegView(RuntimeEnvironment.getApplication());
    MjpegView viaAttrs = new MjpegView(RuntimeEnvironment.getApplication(), null);
    assertNotNull(viaContext);
    assertNotNull(viaAttrs);
    assertNull(viaContext.getTag());
  }
}

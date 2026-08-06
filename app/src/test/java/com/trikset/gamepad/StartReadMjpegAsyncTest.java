package com.trikset.gamepad;

import static org.junit.Assert.assertNull;

import com.demo.mjpeg.MjpegView;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class StartReadMjpegAsyncTest {

  @Test
  public void doInBackgroundShouldReturnNullForNullUrl() {
    MjpegView view = new MjpegView(org.robolectric.RuntimeEnvironment.getApplication());
    StartReadMjpegAsync task = new StartReadMjpegAsync(view);
    assertNull(task.doInBackground((URL) null));
  }

  @Test
  public void doInBackgroundWithUnreachableHostShouldReturnNull() {
    MjpegView view = new MjpegView(org.robolectric.RuntimeEnvironment.getApplication());
    StartReadMjpegAsync task = new StartReadMjpegAsync(view);
    // A definitely-unreachable address; the HttpURLConnection fails and the
    // IOException is caught -> null.
    try {
      URL url = new URL("http://127.0.0.1:1/nope");
      assertNull(task.doInBackground(url));
    } catch (java.net.MalformedURLException e) {
      throw new AssertionError(e);
    }
  }
}

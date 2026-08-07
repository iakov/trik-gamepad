package com.trikset.gamepad;

import android.app.Activity;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

/**
 * ActivityTestRule that waits for the app window to gain focus before each test, dismissing any
 * focus-stealing system overlay (e.g. the first-immersion confirmation) that CI's headless
 * swiftshader GPU leaves in front of the app window.
 *
 * <p>Pass {@code waitForFocus = false} for tests that never interact with the view hierarchy (e.g.
 * KeepAliveTests) so they are not slowed by the wait.
 */
public class FocusAwareActivityTestRule<T extends Activity> extends ActivityTestRule<T> {

  private static final long FOCUS_TIMEOUT_MS = 45_000;
  private static final long FOCUS_POLL_MS = 500;
  private static final long BACK_PRESS_DELAY_MS = 5_000;
  private static final int MAX_BACK_PRESSES = 3;

  private final boolean waitForFocus;

  public FocusAwareActivityTestRule(@NonNull Class<T> activityClass) {
    this(activityClass, true);
  }

  public FocusAwareActivityTestRule(@NonNull Class<T> activityClass, boolean waitForFocus) {
    super(activityClass);
    this.waitForFocus = waitForFocus;
  }

  @Override
  protected void afterActivityLaunched() {
    super.afterActivityLaunched();
    if (waitForFocus) {
      waitForWindowFocus();
    }
  }

  private boolean hasWindowFocus() {
    final boolean[] focused = new boolean[1];
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> focused[0] = getActivity().hasWindowFocus());
    return focused[0];
  }

  private void waitForWindowFocus() {
    final long start = SystemClock.uptimeMillis();
    final long deadline = start + FOCUS_TIMEOUT_MS;
    int backPresses = 0;
    while (SystemClock.uptimeMillis() < deadline) {
      if (hasWindowFocus()) {
        return;
      }
      // A system overlay (e.g. the first-immersion confirmation) can hold window focus
      // indefinitely; dismissing it with BACK lets the app regain focus. Only press while
      // the app window is NOT focused, and never more than a few times, so a healthy
      // activity is never sent BACK.
      if (backPresses < MAX_BACK_PRESSES
          && SystemClock.uptimeMillis() - start >= BACK_PRESS_DELAY_MS) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        backPresses++;
      }
      try {
        Thread.sleep(FOCUS_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    throw new AssertionError("App window never gained focus within " + FOCUS_TIMEOUT_MS + " ms");
  }
}

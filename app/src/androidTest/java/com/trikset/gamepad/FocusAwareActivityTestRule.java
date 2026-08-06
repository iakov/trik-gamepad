package com.trikset.gamepad;

import android.app.Activity;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

/**
 * ActivityTestRule that waits for the app window to gain focus before each test.
 *
 * <p>CI runs the instrumented suite on a headless emulator under the swiftshader software GPU,
 * where the app window can lose focus to system overlays (e.g. the first immersive-mode entry, a
 * slow window manager) for longer than Espresso's per-interaction timeout. Every Espresso
 * interaction then fails with RootViewWithoutFocusException. Waiting here once per test, with a
 * generous budget, absorbs that transient focus loss instead of failing each interaction.
 *
 * <p>This is a strict wait, not a retry: a test whose window never gains focus fails (after the
 * budget), and the CI wrapper's single retry absorbs residual flakes.
 */
public class FocusAwareActivityTestRule<T extends Activity> extends ActivityTestRule<T> {

  private static final long FOCUS_TIMEOUT_MS = 60_000;
  private static final long FOCUS_POLL_MS = 500;

  public FocusAwareActivityTestRule(@NonNull Class<T> activityClass) {
    super(activityClass);
  }

  @Override
  protected void afterActivityLaunched() {
    super.afterActivityLaunched();
    waitForWindowFocus();
  }

  private void waitForWindowFocus() {
    final long deadline = SystemClock.uptimeMillis() + FOCUS_TIMEOUT_MS;
    while (SystemClock.uptimeMillis() < deadline) {
      final boolean[] focused = new boolean[1];
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(() -> focused[0] = getActivity().hasWindowFocus());
      if (focused[0]) {
        return;
      }
      try {
        Thread.sleep(FOCUS_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    throw new AssertionError(
        "App window never gained focus within " + FOCUS_TIMEOUT_MS + " ms");
  }
}

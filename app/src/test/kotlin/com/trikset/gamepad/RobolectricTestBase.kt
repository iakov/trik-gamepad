package com.trikset.gamepad

import org.robolectric.annotation.Config

/**
 * Common Robolectric test base: carries the 3-SDK [Config] triple. Robolectric resolves [Config]
 * (and `@LooperMode`) from superclasses (probed 2026-08-09), so subclasses only need
 * `@RunWith(RobolectricTestRunner::class)` plus their own `@GraphicsMode`/`@LooperMode` where they
 * differ. Note [Config.OLDEST_SDK] is the sentinel -4, resolved to the app minSdk (23).
 */
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
open class RobolectricTestBase

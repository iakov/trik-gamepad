package com.trikset.gamepad

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen

class SettingsActivity :
    AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportFragmentManager
        .beginTransaction()
        .replace(android.R.id.content, SettingsFragment())
        .commit()
  }

  /**
   * PreferenceFragmentCompat 1.2.x has no default sub-screen navigation: a tapped nested
   * PreferenceScreen is silently ignored unless the host handles it (verified against the library
   * bytecode). Re-run [SettingsFragment] against the nested screen's root key so its init* helpers
   * (About, copy-IP, presets) are wired for the sub-screen too; the back stack pops back to the
   * root screen.
   */
  override fun onPreferenceStartScreen(
      caller: PreferenceFragmentCompat,
      pref: PreferenceScreen,
  ): Boolean {
    val args = Bundle()
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
    val fragment = SettingsFragment()
    fragment.arguments = args
    supportFragmentManager
        .beginTransaction()
        .replace(android.R.id.content, fragment)
        .addToBackStack(null)
        .commit()
    return true
  }
}

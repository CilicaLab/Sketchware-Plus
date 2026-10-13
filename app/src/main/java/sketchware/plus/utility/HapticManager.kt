package sketchware.plus.utility

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import sketchware.plus.SketchApplication
import java.lang.ref.WeakReference

/**
 * Mapping (Tremor style, one system-tuned effect per action):
 *  Save / Run      -> View CONFIRM
 *  Stop            -> EFFECT_DOUBLE_CLICK
 *  Done            -> View SEGMENT_TICK
 *  Switch on / off -> View TOGGLE_ON / TOGGLE_OFF
 *
 * View constants need a View: pass one, or call setAnchor(window.decorView) in each Activity.
 */
object HapticManager {

    private const val PREFS = "P12"
    private const val KEY_ENABLED = "P12I0"

    private var anchor: WeakReference<View>? = null
    private var accessibilityAttrs: VibrationAttributes? = null

    @JvmStatic
    fun setAnchor(v: View?) {
        anchor = v?.let { WeakReference(it) }
    }

    @JvmStatic
    @JvmOverloads
    fun isHapticsEnabled(v: View? = null): Boolean {
        val context = SketchApplication.getContext() ?: return true
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
        } catch (_: Exception) {
            true
        }
    }

    // ───────────────────────── Public API ─────────────────────────

    /** Save: CONFIRM (API 30+), fallback EFFECT_CLICK. */
    @JvmStatic
    @JvmOverloads
    fun vibrateSave(v: View? = null) {
        if (!isHapticsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && viewHaptic(v, HapticFeedbackConstants.CONFIRM)) return
        playEffect(VibrationEffect.EFFECT_CLICK, 30)
    }

    /** Run: CONFIRM (API 30+), fallback EFFECT_CLICK. */
    @JvmStatic
    @JvmOverloads
    fun vibrateRun(v: View? = null) {
        if (!isHapticsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && viewHaptic(v, HapticFeedbackConstants.CONFIRM)) return
        playEffect(VibrationEffect.EFFECT_CLICK, 30)
    }

    /** Stop: EFFECT_DOUBLE_CLICK. */
    @JvmStatic
    @JvmOverloads
    fun vibrateStop(v: View? = null) {
        if (!isHapticsEnabled()) return
        playEffect(VibrationEffect.EFFECT_DOUBLE_CLICK, 40)
    }

    /** Assistant done: SEGMENT_TICK (API 34+), fallback EFFECT_TICK. */
    @JvmStatic
    fun vibrateAssistantDone() {
        if (!isHapticsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            viewHaptic(null, HapticFeedbackConstants.SEGMENT_TICK)
        ) return
        playEffect(VibrationEffect.EFFECT_TICK, 20)
    }

    /** Switch: TOGGLE_ON / TOGGLE_OFF (API 34+), fallback EFFECT_CLICK / EFFECT_TICK. */
    @JvmStatic
    @JvmOverloads
    fun vibrateSwitch(v: View? = null, isChecked: Boolean = true) {
        if (!isHapticsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val constant = if (isChecked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            if (viewHaptic(v, constant)) return
        }
        playEffect(
            if (isChecked) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK,
            if (isChecked) 35 else 20
        )
    }

    @JvmStatic
    @JvmOverloads
    fun vibrateS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.TOGGLE_ON
        }
        playEffect(
            VibrationEffect.EFFECT_CLICK,
            35
        )
    }









    @JvmStatic
    fun attachSwitchHaptics(group: PreferenceGroup?) {
        if (group == null) return
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is TwoStatePreference) {
                val existing = pref.onPreferenceChangeListener
                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { p, newValue ->
                    val allowed = existing?.onPreferenceChange(p, newValue) ?: true
                    if (allowed) vibrateSwitch(null, newValue == true)
                    allowed
                }
            } else if (pref is PreferenceGroup) {
                attachSwitchHaptics(pref)
            }
        }
    }

    // ───────────────────────── Internals ─────────────────────────

    private fun viewHaptic(v: View?, constant: Int): Boolean {
        val target = v ?: anchor?.get()?.takeIf { it.isAttachedToWindow } ?: return false
        target.isHapticFeedbackEnabled = true
        return target.performHapticFeedback(constant)
    }

    /** Same as Tremor.playEffect(): createPredefined, no support check, USAGE_ACCESSIBILITY on 33+. */
    private fun playEffect(effectId: Int, legacyMs: Long) {
        val vib = getVibrator(SketchApplication.getContext()) ?: return
        if (!vib.hasVibrator()) return

        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(effectId)
            } else {
                VibrationEffect.createOneShot(legacyMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = accessibilityAttrs ?: VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
                    .build()
                    .also { accessibilityAttrs = it }
                vib.vibrate(effect, attrs)
            } else {
                vib.vibrate(effect)
            }
        } catch (_: RuntimeException) {
        }
    }

    private fun getVibrator(context: Context?): Vibrator? {
        if (context == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}
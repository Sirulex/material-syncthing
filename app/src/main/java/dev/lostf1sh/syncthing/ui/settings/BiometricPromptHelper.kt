package dev.lostf1sh.syncthing.ui.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricPromptHelper {

    private val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun show(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        // Pre-check: calling BiometricPrompt.authenticate() on a device that
        // has no enrolled authenticator (or an unsupported sensor) throws a
        // SecurityException at the system level, bypassing the app-level
        // AuthenticationCallback entirely.  Detect these cases here and surface
        // them as a normal onError() call instead.
        val biometricManager = BiometricManager.from(activity)
        when (val status = biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit // proceed
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                return onError("No biometric or screen-lock credential enrolled. Set one up in system Settings.")

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                return onError("This device has no biometric or screen-lock hardware.")

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                return onError("Biometric hardware is currently unavailable. Try again later.")

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                return onError("A security update is required before biometric authentication can be used.")

            else ->
                return onError("Biometric authentication is not available (code $status).")
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Material Syncthing")
            .setSubtitle("Authentication required")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }
}

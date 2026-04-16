package dev.lostf1sh.syncthing.ui.qr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceIdValidatorTest {

    @Test
    fun `valid device ID returns true`() {
        val id = "MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2"
        assertThat(DeviceIdValidator.isValid(id)).isTrue()
    }

    @Test
    fun `lowercase is valid`() {
        val id = "mfzwi3d-borsxa2-lnfspm4-yfber5y-6dy2ht3-mvzhqn6-q42xqr7-qhgxmh2"
        assertThat(DeviceIdValidator.isValid(id)).isTrue()
    }

    @Test
    fun `too few groups is invalid`() {
        val id = "MFZWI3D-BORSXA2-LNFSPM4"
        assertThat(DeviceIdValidator.isValid(id)).isFalse()
    }

    @Test
    fun `wrong chars is invalid`() {
        val id = "MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXM!2"
        assertThat(DeviceIdValidator.isValid(id)).isFalse()
    }

    @Test
    fun `empty string is invalid`() {
        assertThat(DeviceIdValidator.isValid("")).isFalse()
    }

    @Test
    fun `extract cleans whitespace`() {
        val id = "  mfzwi3d-borsxa2-lnfspm4-yfber5y-6dy2ht3-mvzhqn6-q42xqr7-qhgxmh2  "
        val result = DeviceIdValidator.extract(id)
        assertThat(result).isNotNull()
        assertThat(result).startsWith("MFZWI3D")
    }

    @Test
    fun `extract returns null for garbage`() {
        assertThat(DeviceIdValidator.extract("not a device id")).isNull()
    }

    @Test
    fun `extract handles newlines and tabs`() {
        val id = "mfzwi3d-borsxa2\n-lnfspm4-yfber5y\t-6dy2ht3-mvzhqn6-q42xqr7-qhgxmh2"
        assertThat(DeviceIdValidator.extract(id)).isEqualTo(
            "MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2"
        )
    }

    @Test
    fun `extract normalizes unicode dashes`() {
        // Uses en-dash (U+2013) and em-dash (U+2014) instead of ASCII '-'
        val id = "MFZWI3D\u2013BORSXA2\u2014LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2"
        assertThat(DeviceIdValidator.extract(id)).isNotNull()
    }

    @Test
    fun `extract finds ID embedded in surrounding text`() {
        val text = "Here is my device: MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2 share it"
        assertThat(DeviceIdValidator.extract(text))
            .isEqualTo("MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2")
    }

    @Test
    fun `chars outside base32 alphabet are rejected`() {
        // 0, 1, 8, 9 are not in the Syncthing base32 alphabet.
        val id = "MFZWI30-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH1"
        assertThat(DeviceIdValidator.isValid(id)).isFalse()
    }

    @Test
    fun `zero-width space is stripped`() {
        val id = "MFZWI3D-BORSXA2-LNFSPM4-YFBER5Y-6DY2HT3-MVZHQN6-Q42XQR7-QHGXMH2\u200B"
        assertThat(DeviceIdValidator.isValid(id)).isTrue()
    }
}

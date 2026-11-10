package com.asl.wallet.core.designsystem

import androidx.compose.ui.text.AnnotatedString
import com.asl.wallet.core.designsystem.component.GroupedDigits
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupedDigitsTest {
    private val g = GroupedDigits()

    @Test fun `Aadhaar shows as 4-4-4 and the cursor maps across the spaces`() {
        val t = g.filter(AnnotatedString("999941057058"))
        assertThat(t.text.text).isEqualTo("9999 4105 7058")
        val m = t.offsetMapping
        assertThat(m.originalToTransformed(0)).isEqualTo(0)
        assertThat(m.originalToTransformed(4)).isEqualTo(4)   // end of the first group, before the space
        assertThat(m.originalToTransformed(5)).isEqualTo(6)   // after the space
        assertThat(m.originalToTransformed(12)).isEqualTo(14)
        assertThat(m.transformedToOriginal(5)).isEqualTo(4)   // tapping right after a space
        assertThat(m.transformedToOriginal(14)).isEqualTo(12)
    }

    @Test fun `partial numbers have no trailing space`() {
        assertThat(g.filter(AnnotatedString("1234")).text.text).isEqualTo("1234")
        assertThat(g.filter(AnnotatedString("12345")).text.text).isEqualTo("1234 5")
        assertThat(g.filter(AnnotatedString("")).text.text).isEqualTo("")
    }
}

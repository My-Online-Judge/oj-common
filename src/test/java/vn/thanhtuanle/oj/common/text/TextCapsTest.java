package vn.thanhtuanle.oj.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCapsTest {

    @Test
    void shortTextIsUnchanged() {
        String text = "a".repeat(TextCaps.SAMPLE_MAX_CHARS);
        assertThat(TextCaps.cap(text, TextCaps.SAMPLE_MAX_CHARS)).isSameAs(text);
    }

    @Test
    void longTextIsCutAndMarked() {
        String result = TextCaps.cap("a".repeat(70_000), TextCaps.SAMPLE_MAX_CHARS);
        assertThat(result).hasSize(TextCaps.SAMPLE_MAX_CHARS + TextCaps.TRUNCATION_MARK.length());
        assertThat(result).endsWith(TextCaps.TRUNCATION_MARK);
    }

    @Test
    void neverSplitsASurrogatePair() {
        String text = "a".repeat(TextCaps.SAMPLE_MAX_CHARS - 1) + "😀" + "b".repeat(100);
        String result = TextCaps.cap(text, TextCaps.SAMPLE_MAX_CHARS);
        assertThat(result).endsWith(TextCaps.TRUNCATION_MARK);
        char beforeMark = result.charAt(result.length() - TextCaps.TRUNCATION_MARK.length() - 1);
        assertThat(Character.isHighSurrogate(beforeMark)).isFalse();
        assertThat(result).hasSize(TextCaps.SAMPLE_MAX_CHARS - 1 + TextCaps.TRUNCATION_MARK.length());
    }

    @Test
    void moreFollowsMarksEvenShortText() {
        assertThat(TextCaps.cap("hi", 10, true)).isEqualTo("hi" + TextCaps.TRUNCATION_MARK);
        assertThat(TextCaps.cap("hi", 10, false)).isEqualTo("hi");
    }

    @Test
    void nullStaysNull() {
        assertThat(TextCaps.cap(null, 10)).isNull();
        assertThat(TextCaps.cap(null, 10, true)).isNull();
    }
}

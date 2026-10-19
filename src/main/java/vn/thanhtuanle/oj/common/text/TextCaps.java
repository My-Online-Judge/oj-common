package vn.thanhtuanle.oj.common.text;

/** Caps user-visible text to a bounded size without splitting a UTF-16 surrogate pair. */
public final class TextCaps {

    public static final int SAMPLE_MAX_CHARS = 65536;
    public static final String TRUNCATION_MARK = "\n…[truncated]";

    private TextCaps() {
    }

    public static String cap(String text, int maxChars) {
        return cap(text, maxChars, false);
    }

    /** As {@link #cap(String, int)}; {@code moreFollows} marks the text even when it fits. */
    public static String cap(String text, int maxChars, boolean moreFollows) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxChars) {
            return moreFollows ? text + TRUNCATION_MARK : text;
        }
        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + TRUNCATION_MARK;
    }
}

package vn.thanhtuanle.oj.common.web.payload;

/** The most rows a list endpoint returns per page. A larger request is clamped, not refused. */
public final class PageSizes {

    public static final int MAX = 100;

    private PageSizes() {
    }

    /** {@code min(requested, MAX)}. Values below 1 pass through, so PageRequest still answers 400. */
    public static int cap(int requested) {
        return Math.min(requested, MAX);
    }
}

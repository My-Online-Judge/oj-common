package vn.thanhtuanle.oj.common.web.payload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageSizesTest {

    @Test
    void capsAtOneHundredAndPassesTheRestThrough() {
        assertThat(PageSizes.cap(500)).isEqualTo(100);
        assertThat(PageSizes.cap(100)).isEqualTo(100);
        assertThat(PageSizes.cap(10)).isEqualTo(10);
        assertThat(PageSizes.cap(0)).isZero();
        assertThat(PageSizes.cap(-5)).isEqualTo(-5);
    }
}

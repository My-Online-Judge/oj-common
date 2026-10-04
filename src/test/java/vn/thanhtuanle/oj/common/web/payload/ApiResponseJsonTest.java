package vn.thanhtuanle.oj.common.web.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response envelope every OJ service answers with, pinned byte for byte: the portal reads these fields, and
 * moving the class into oj-common (sub-project 3a) must not change one of them.
 */
class ApiResponseJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    private String write(Object value) throws Exception {
        return json.writeValueAsString(value).replaceAll("\"timestamp\":\"\\d{14}\"", "\"timestamp\":\"T\"");
    }

    @Test
    void aSuccessCarriesStatusMessageDataAndTimestamp() throws Exception {
        assertThat(write(ApiResponse.success(Map.of("x", 1))))
                .isEqualTo("{\"status\":200,\"message\":\"Success\",\"data\":{\"x\":1},\"timestamp\":\"T\"}");
    }

    @Test
    void anErrorCarriesItsFieldErrorsAndNoData() throws Exception {
        assertThat(write(ApiResponse.error(400, "Title is required", Map.of("title", "Title is required"))))
                .isEqualTo("{\"status\":400,\"message\":\"Title is required\","
                        + "\"errors\":{\"title\":\"Title is required\"},\"timestamp\":\"T\"}");
    }

    @Test
    void aPageMovesItsRowsToDataAndTheRestToPagination() throws Exception {
        PageResponse<String> page = PageResponse.of(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));

        assertThat(write(ApiResponse.success(page)))
                .isEqualTo("{\"status\":200,\"message\":\"Success\",\"data\":[\"a\",\"b\"],\"timestamp\":\"T\","
                        + "\"pagination\":{\"page\":1,\"size\":2,\"totalElements\":5,\"totalPages\":3,"
                        + "\"hasPrev\":true,\"hasNext\":true,\"prevPage\":0,\"nextPage\":2}}");
    }
}

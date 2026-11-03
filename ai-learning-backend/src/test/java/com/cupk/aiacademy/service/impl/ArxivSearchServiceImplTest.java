package com.cupk.aiacademy.service.impl;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArxivSearchServiceImplTest {
    private static final String EMPTY_FEED = "<feed xmlns=\"http://www.w3.org/2005/Atom\"/>";

    private Result search(Interceptor interceptor) {
        ArxivSearchServiceImpl service = new ArxivSearchServiceImpl();
        ReflectionTestUtils.setField(service, "client", new OkHttpClient.Builder().addInterceptor(interceptor).build());
        return service.search("machine learning", 1, 10, "relevance");
    }

    private Result response(int code, String body) {
        return search(chain -> new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture").body(ResponseBody.create(body, MediaType.get("application/atom+xml"))).build());
    }

    private void assertFailure(Result result, String code) {
        assertEquals(code, result.getCode());
        assertNull(result.getData());
    }

    @Test void timeoutIs504() {
        assertFailure(search(chain -> { throw new SocketTimeoutException("fixture"); }), "504");
    }

    @Test void unreachableIs503() {
        assertFailure(search(chain -> { throw new IOException("fixture"); }), "503");
    }

    @Test void rateLimitRemains429() {
        assertFailure(response(429, ""), "429");
    }

    @Test void upstreamFailureIs502() {
        assertFailure(response(503, ""), "502");
    }

    @Test void arxivErrorFeedIs502() {
        assertFailure(response(200, "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry>"
            + "<id>http://arxiv.org/api/errors#incorrect_id_format</id><title>Error</title>"
            + "<summary>Invalid query</summary></entry></feed>"), "502");
    }

    @Test void nonAtomRootIs502() {
        assertFailure(response(200, "<html><title>Gateway error</title></html>"), "502");
    }

    @Test void doctypeWithExternalEntityIsRejected() throws Exception {
        Path marker = Files.createTempFile("arxiv-entity", ".txt");
        try {
            Files.writeString(marker, "EXTERNAL_ENTITY_MARKER");
            String xml = "<!DOCTYPE feed [<!ENTITY xxe SYSTEM \"" + marker.toUri() + "\">]>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><id>http://arxiv.org/abs/1234.5678</id>"
                + "<title>&xxe;</title></entry></feed>";
            assertFailure(response(200, xml), "502");
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    @Test void emptyAtomFeedIsSuccessful() {
        Result result = response(200, EMPTY_FEED);
        assertEquals("200", result.getCode());
        PaperSearchResponse data = (PaperSearchResponse) result.getData();
        assertEquals(0, data.getTotal());
        assertTrue(data.getData().isEmpty());
    }

    @Test void nonEmptyAtomFeedPreservesPaperFields() {
        String xml = "<feed xmlns=\"http://www.w3.org/2005/Atom\""
            + " xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\">"
            + "<opensearch:totalResults>42</opensearch:totalResults><entry>"
            + "<id>https://arxiv.org/abs/1234.5678</id><title>Example paper</title>"
            + "<summary>Example abstract</summary><author><name>Ada Example</name></author>"
            + "<link title=\"pdf\" href=\"https://arxiv.org/pdf/1234.5678\"/>"
            + "</entry></feed>";
        Result result = response(200, xml);
        assertEquals("200", result.getCode());
        PaperSearchResponse data = (PaperSearchResponse) result.getData();
        assertEquals(42, data.getTotal());
        assertEquals(1, data.getData().size());
        PaperSearchResponse.Paper paper = data.getData().get(0);
        assertEquals("1234.5678", paper.getPaperId());
        assertEquals("Example paper", paper.getTitle());
        assertEquals("Example abstract", paper.getAbstractText());
        assertEquals("Ada Example", paper.getAuthors().get(0).getName());
        assertEquals("https://arxiv.org/pdf/1234.5678", paper.getOpenAccessPdf().getUrl());
    }
}

package com.walmartlabs.concord.server.process.queue;

import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.server.process.queue.ProcessFilter.FilterType.REGEXP_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FilterUtilsTest {

    @Test
    public void testRegularExpressionMatch() {
        MultivaluedHashMap<String, String> m = new MultivaluedHashMap<>();
        m.put("test.rm", Collections.singletonList("myValue"));

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(m);

        List<ProcessFilter.JsonFilter> filter =  FilterUtils.parseJson("test", uriInfo);
        assertEquals(1, filter.size());
        assertEquals(REGEXP_MATCH, filter.get(0).type());
        assertEquals("myValue", filter.get(0).value());
    }
}

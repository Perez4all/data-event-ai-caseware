package com.caseware.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private SearchClient searchClient;

    @BeforeEach
    void setUp() throws Exception {
        searchClient = new SearchClient(restClient);
        Field rankerUrlField = SearchClient.class.getDeclaredField("rankerUrl");
        rankerUrlField.setAccessible(true);
        rankerUrlField.set(searchClient, "http://localhost:8000");
    }

    @Test
    void refreshIndex_postsToRankerRefreshEndpoint() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("http://localhost:8000/refresh")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        searchClient.refreshIndex();

        verify(restClient).post();
        verify(requestBodyUriSpec).uri("http://localhost:8000/refresh");
        verify(requestBodySpec).retrieve();
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void refreshIndex_usesConfiguredRankerUrl() throws Exception {
        Field rankerUrlField = SearchClient.class.getDeclaredField("rankerUrl");
        rankerUrlField.setAccessible(true);
        rankerUrlField.set(searchClient, "http://ranker:9000");

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("http://ranker:9000/refresh")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        searchClient.refreshIndex();

        verify(requestBodyUriSpec).uri("http://ranker:9000/refresh");
    }

    @Test
    void refreshIndex_propagatesException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> searchClient.refreshIndex());
    }
}


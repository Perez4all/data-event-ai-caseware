package com.caseware.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SearchClient {

    @Value("${ranker.url}")
    private String rankerUrl;

    private final RestClient restClient;

    public SearchClient(RestClient restClient){
        this.restClient = restClient;
    }

    @Async
    public void refreshIndex(){
        restClient.post()
                .uri(rankerUrl + "/refresh")
                .retrieve()
                .toBodilessEntity();
    }

}

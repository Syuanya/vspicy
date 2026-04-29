package com.vspicy.video.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MemberClient {
    private final RestClient restClient;
    private final String memberBaseUrl;

    public MemberClient(
            RestClient.Builder builder,
            @Value("${vspicy.member.url:http://localhost:18092}") String memberBaseUrl
    ) {
        this.restClient = builder.build();
        this.memberBaseUrl = memberBaseUrl;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkUpload(Long userId, Long sizeMb) {
        return restClient.get()
                .uri(memberBaseUrl + "/api/members/check/upload?userId={userId}&sizeMb={sizeMb}", userId, sizeMb)
                .retrieve()
                .body(Map.class);
    }
}

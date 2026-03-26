package com.example.demo.service;

import com.example.demo.dao.Coordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private static final String KAKAO_URL =
            "https://dapi.kakao.com/v2/local/search/address.json";

    public Coordinate getCoordinates(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("주소값이 비어 있습니다.");
        }

        RestTemplate restTemplate = new RestTemplate();

        String normalizedAddress = address.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        URI uri = UriComponentsBuilder.fromHttpUrl(KAKAO_URL)
                .queryParam("query", normalizedAddress)
                .encode()
                .build()
                .toUri();

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }

            List<Map<String, Object>> documents =
                    (List<Map<String, Object>>) body.get("documents");

            if (documents != null && !documents.isEmpty()) {
                Map<String, Object> firstDoc = documents.get(0);

                Double x = Double.parseDouble(firstDoc.get("x").toString()); // 경도
                Double y = Double.parseDouble(firstDoc.get("y").toString()); // 위도

                return new Coordinate(x, y);
            }

            return null;

        } catch (HttpClientErrorException e) {
            System.out.println("카카오 API 호출 실패");
            System.out.println("status = " + e.getStatusCode());
            System.out.println("response = " + e.getResponseBodyAsString());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
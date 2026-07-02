package com.polymarket.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.polymarket.model.gamma.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the Polymarket Gamma REST API (https://gamma-api.polymarket.com).
 */
public final class GammaClient {

    private static final String DEFAULT_HOST = "https://gamma-api.polymarket.com";

    private final String host;
    private final HttpClient http;

    private GammaClient(Builder builder) {
        this.host = builder.host != null ? builder.host : DEFAULT_HOST;
        this.http = builder.http != null ? builder.http : new HttpClient();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String host;
        private HttpClient http;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        public GammaClient build() {
            return new GammaClient(this);
        }
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    public String status() throws IOException {
        return http.get(host + "/status", Collections.emptyMap()).trim();
    }

    // -------------------------------------------------------------------------
    // Teams / Sports
    // -------------------------------------------------------------------------

    public List<GammaTeam> teams(TeamsRequest req) throws IOException {
        String json = http.get(url("teams", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTeam>>() {});
    }

    public List<GammaSportsMetadata> sports() throws IOException {
        String json = http.get(url("sports", null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaSportsMetadata>>() {});
    }

    public GammaSportsMarketTypesResponse sportsMarketTypes() throws IOException {
        String json = http.get(url("sports/market-types", null), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaSportsMarketTypesResponse.class);
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    public List<GammaTag> tags(TagsRequest req) throws IOException {
        String json = http.get(url("tags", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTag>>() {});
    }

    public GammaTag tagById(TagByIdRequest req) throws IOException {
        String json = http.get(url("tags/" + req.getId(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaTag.class);
    }

    public GammaTag tagBySlug(TagBySlugRequest req) throws IOException {
        String json = http.get(url("tags/slug/" + req.getSlug(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaTag.class);
    }

    public List<GammaRelatedTag> relatedTagsById(RelatedTagsByIdRequest req) throws IOException {
        String json = http.get(url("tags/" + req.getId() + "/related-tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaRelatedTag>>() {});
    }

    public List<GammaRelatedTag> relatedTagsBySlug(RelatedTagsBySlugRequest req) throws IOException {
        String json = http.get(url("tags/slug/" + req.getSlug() + "/related-tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaRelatedTag>>() {});
    }

    public List<GammaTag> tagsRelatedToTagById(RelatedTagsByIdRequest req) throws IOException {
        String json = http.get(url("tags/" + req.getId() + "/related-tags/tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTag>>() {});
    }

    public List<GammaTag> tagsRelatedToTagBySlug(RelatedTagsBySlugRequest req) throws IOException {
        String json = http.get(url("tags/slug/" + req.getSlug() + "/related-tags/tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTag>>() {});
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    public List<GammaEvent> events(EventsRequest req) throws IOException {
        String json = http.get(url("events", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaEvent>>() {});
    }

    public GammaEvent eventById(EventByIdRequest req) throws IOException {
        String json = http.get(url("events/" + req.getId(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaEvent.class);
    }

    public GammaEvent eventBySlug(EventBySlugRequest req) throws IOException {
        String json = http.get(url("events/slug/" + req.getSlug(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaEvent.class);
    }

    public List<GammaTag> eventTags(EventTagsRequest req) throws IOException {
        String json = http.get(url("events/" + req.getId() + "/tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTag>>() {});
    }

    /** Keyset (cursor) pagination over events. Set {@code afterCursor} from the prior response's
     *  {@code nextCursor}; do not set {@code offset} (the endpoint rejects it). */
    public EventsKeysetResponse eventsKeyset(EventsRequest req) throws IOException {
        String json = http.get(url("events/keyset", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, EventsKeysetResponse.class);
    }

    // -------------------------------------------------------------------------
    // Markets
    // -------------------------------------------------------------------------

    public List<GammaMarketDetail> markets(MarketsRequest req) throws IOException {
        String json = http.get(url("markets", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaMarketDetail>>() {});
    }

    public GammaMarketDetail marketById(MarketByIdRequest req) throws IOException {
        String json = http.get(url("markets/" + req.getId(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaMarketDetail.class);
    }

    public GammaMarketDetail marketBySlug(MarketBySlugRequest req) throws IOException {
        String json = http.get(url("markets/slug/" + req.getSlug(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaMarketDetail.class);
    }

    public List<GammaTag> marketTags(MarketTagsRequest req) throws IOException {
        String json = http.get(url("markets/" + req.getId() + "/tags", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaTag>>() {});
    }

    /** Keyset (cursor) pagination over markets. Set {@code afterCursor} from the prior response's
     *  {@code nextCursor}; do not set {@code offset} (the endpoint rejects it). */
    public MarketsKeysetResponse marketsKeyset(MarketsRequest req) throws IOException {
        String json = http.get(url("markets/keyset", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, MarketsKeysetResponse.class);
    }

    // -------------------------------------------------------------------------
    // Series
    // -------------------------------------------------------------------------

    public List<GammaSeries> seriesList(SeriesListRequest req) throws IOException {
        String json = http.get(url("series", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaSeries>>() {});
    }

    public GammaSeries seriesById(SeriesByIdRequest req) throws IOException {
        String json = http.get(url("series/" + req.getId(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaSeries.class);
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------

    public List<GammaComment> comments(CommentsRequest req) throws IOException {
        String json = http.get(url("comments", req != null ? req.toQueryParams() : null), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaComment>>() {});
    }

    public List<GammaComment> commentsById(CommentsByIdRequest req) throws IOException {
        String json = http.get(url("comments/" + req.getId(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaComment>>() {});
    }

    public List<GammaComment> commentsByUserAddress(CommentsByUserAddressRequest req) throws IOException {
        String json = http.get(url("comments/user_address/" + req.getUserAddress(), req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, new TypeReference<List<GammaComment>>() {});
    }

    // -------------------------------------------------------------------------
    // Profiles / Search
    // -------------------------------------------------------------------------

    public GammaPublicProfile publicProfile(PublicProfileRequest req) throws IOException {
        String json = http.get(url("public-profile", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaPublicProfile.class);
    }

    public GammaSearchResults search(SearchRequest req) throws IOException {
        String json = http.get(url("public-search", req.toQueryParams()), Collections.emptyMap());
        return http.objectMapper().readValue(json, GammaSearchResults.class);
    }

    // -------------------------------------------------------------------------
    // URL helper
    // -------------------------------------------------------------------------

    private String url(String path, List<Map.Entry<String, String>> params) {
        String base = host + "/" + path;
        if (params == null || params.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append("?");
        for (Map.Entry<String, String> e : params) {
            sb.append(e.getKey()).append("=")
              .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).append("&");
        }
        return sb.substring(0, sb.length() - 1);
    }
}

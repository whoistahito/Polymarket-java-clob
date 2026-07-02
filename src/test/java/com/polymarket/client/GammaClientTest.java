package com.polymarket.client;

import com.polymarket.model.gamma.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.HttpUrl;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GammaClient Tests")
class GammaClientTest {

    private MockWebServer server;
    private GammaClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new GammaClient.Builder()
                .host(server.url("").toString().replaceAll("/$", ""))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static Map<String, List<String>> toMultiMap(List<Map.Entry<String, String>> entries) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) {
            m.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
        return m;
    }

    private static Map<String, String> toSingleMap(List<Map.Entry<String, String>> entries) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) m.put(e.getKey(), e.getValue());
        return m;
    }

    private void enqueue(String json) {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(json)
                .addHeader("Content-Type", "application/json"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-001: status()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-001: status() returns OK")
    void testStatus() throws IOException {
        enqueue("OK");
        String result = client.status();
        assertEquals("OK", result);
    }

    // -----------------------------------------------------------------------
    // TC-GC-002: events() deserializes list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-002: events(EventsRequest) deserializes event list")
    void testEvents() throws IOException {
        enqueue("[{\"id\":\"evt-1\",\"slug\":\"test-event\",\"title\":\"Test Event\",\"active\":true,\"closed\":false}]");

        List<GammaEvent> events = client.events(EventsRequest.builder().limit(10).active(true).build());

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("evt-1", events.get(0).id());
        assertEquals("Test Event", events.get(0).title());
        assertTrue(events.get(0).active());
    }

    // -----------------------------------------------------------------------
    // TC-GC-003: eventById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-003: eventById() deserializes single event")
    void testEventById() throws IOException {
        enqueue("{\"id\":\"123\",\"slug\":\"my-event\",\"title\":\"My Event\",\"closed\":false}");

        GammaEvent event = client.eventById(EventByIdRequest.builder().id("123").build());

        assertNotNull(event);
        assertEquals("123", event.id());
        assertEquals("my-event", event.slug());
    }

    // -----------------------------------------------------------------------
    // TC-GC-004: eventBySlug()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-004: eventBySlug() deserializes single event")
    void testEventBySlug() throws IOException {
        enqueue("{\"id\":\"456\",\"slug\":\"slug-event\",\"title\":\"Slug Event\"}");

        GammaEvent event = client.eventBySlug(EventBySlugRequest.builder().slug("slug-event").build());

        assertNotNull(event);
        assertEquals("456", event.id());
        assertEquals("slug-event", event.slug());
    }

    // -----------------------------------------------------------------------
    // TC-GC-005: eventTags()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-005: eventTags() returns tag list")
    void testEventTags() throws IOException {
        enqueue("[{\"id\":\"tag-1\",\"label\":\"Politics\",\"slug\":\"politics\"}]");

        List<GammaTag> tags = client.eventTags(EventTagsRequest.builder().id("evt-1").build());

        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertEquals("tag-1", tags.get(0).id());
        assertEquals("Politics", tags.get(0).label());
    }

    // -----------------------------------------------------------------------
    // TC-GC-006: markets() with embedded JSON fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-006: markets(MarketsRequest) deserializes market list with embedded JSON fields")
    void testMarkets() throws IOException {
        enqueue("[{\"id\":\"mkt-1\",\"question\":\"Will it rain?\",\"active\":true,"
                + "\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\","
                + "\"outcomePrices\":\"[\\\"0.6\\\",\\\"0.4\\\"]\","
                + "\"clobTokenIds\":\"[\\\"token-yes\\\",\\\"token-no\\\"]\"}]");

        List<GammaMarketDetail> markets = client.markets(MarketsRequest.builder().limit(5).build());

        assertNotNull(markets);
        assertEquals(1, markets.size());
        GammaMarketDetail m = markets.get(0);
        assertEquals("mkt-1", m.id());
        assertEquals(List.of("Yes", "No"), m.outcomes());
        assertEquals(List.of("0.6", "0.4"), m.outcomePrices());
        assertEquals(List.of("token-yes", "token-no"), m.clobTokenIds());
    }

    // -----------------------------------------------------------------------
    // TC-GC-007: marketById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-007: marketById() deserializes single market")
    void testMarketById() throws IOException {
        enqueue("{\"id\":\"mkt-42\",\"question\":\"Test?\",\"active\":true}");

        GammaMarketDetail market = client.marketById(MarketByIdRequest.builder().id("mkt-42").build());

        assertNotNull(market);
        assertEquals("mkt-42", market.id());
    }

    // -----------------------------------------------------------------------
    // TC-GC-008: marketBySlug()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-008: marketBySlug() deserializes single market")
    void testMarketBySlug() throws IOException {
        enqueue("{\"id\":\"mkt-99\",\"slug\":\"will-it-rain\",\"question\":\"Will it rain?\"}");

        GammaMarketDetail market = client.marketBySlug(MarketBySlugRequest.builder().slug("will-it-rain").build());

        assertNotNull(market);
        assertEquals("mkt-99", market.id());
        assertEquals("will-it-rain", market.slug());
    }

    // -----------------------------------------------------------------------
    // TC-GC-009: marketTags()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-009: marketTags() returns tag list")
    void testMarketTags() throws IOException {
        enqueue("[{\"id\":\"t1\",\"label\":\"Sports\",\"slug\":\"sports\"}]");

        List<GammaTag> tags = client.marketTags(MarketTagsRequest.builder().id("mkt-1").build());

        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertEquals("Sports", tags.get(0).label());
    }

    // -----------------------------------------------------------------------
    // TC-GC-010: tags()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-010: tags(TagsRequest) returns tag list")
    void testTags() throws IOException {
        enqueue("[{\"id\":\"t1\",\"label\":\"Crypto\",\"slug\":\"crypto\"},{\"id\":\"t2\",\"label\":\"Sports\",\"slug\":\"sports\"}]");

        List<GammaTag> tags = client.tags(TagsRequest.builder().limit(50).build());

        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertEquals("Crypto", tags.get(0).label());
    }

    // -----------------------------------------------------------------------
    // TC-GC-011: tagById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-011: tagById() returns single tag")
    void testTagById() throws IOException {
        enqueue("{\"id\":\"t1\",\"label\":\"Crypto\",\"slug\":\"crypto\",\"isCarousel\":true}");

        GammaTag tag = client.tagById(TagByIdRequest.builder().id("t1").build());

        assertNotNull(tag);
        assertEquals("t1", tag.id());
        assertEquals("Crypto", tag.label());
        assertTrue(tag.isCarousel());
    }

    // -----------------------------------------------------------------------
    // TC-GC-012: tagBySlug()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-012: tagBySlug() returns single tag")
    void testTagBySlug() throws IOException {
        enqueue("{\"id\":\"t2\",\"label\":\"Sports\",\"slug\":\"sports\"}");

        GammaTag tag = client.tagBySlug(TagBySlugRequest.builder().slug("sports").build());

        assertNotNull(tag);
        assertEquals("t2", tag.id());
        assertEquals("sports", tag.slug());
    }

    // -----------------------------------------------------------------------
    // TC-GC-013: relatedTagsById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-013: relatedTagsById() returns related tag list")
    void testRelatedTagsById() throws IOException {
        enqueue("[{\"id\":\"rt-1\",\"tagId\":\"t1\",\"relatedTagId\":\"t2\",\"rank\":1}]");

        List<GammaRelatedTag> related = client.relatedTagsById(RelatedTagsByIdRequest.builder().id("t1").build());

        assertNotNull(related);
        assertEquals(1, related.size());
        assertEquals("t1", related.get(0).tagId());
        assertEquals("t2", related.get(0).relatedTagId());
    }

    // -----------------------------------------------------------------------
    // TC-GC-014: seriesList()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-014: seriesList() returns series list")
    void testSeriesList() throws IOException {
        enqueue("[{\"id\":\"s-1\",\"slug\":\"world-cup-2026\",\"title\":\"World Cup 2026\",\"active\":true}]");

        List<GammaSeries> series = client.seriesList(SeriesListRequest.builder().limit(10).build());

        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("s-1", series.get(0).id());
        assertEquals("World Cup 2026", series.get(0).title());
    }

    // -----------------------------------------------------------------------
    // TC-GC-015: seriesById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-015: seriesById() returns single series")
    void testSeriesById() throws IOException {
        enqueue("{\"id\":\"s-99\",\"slug\":\"nba-2025\",\"title\":\"NBA 2025\"}");

        GammaSeries s = client.seriesById(SeriesByIdRequest.builder().id("s-99").build());

        assertNotNull(s);
        assertEquals("s-99", s.id());
        assertEquals("NBA 2025", s.title());
    }

    // -----------------------------------------------------------------------
    // TC-GC-016: comments()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-016: comments() returns comment list")
    void testComments() throws IOException {
        enqueue("[{\"id\":\"c-1\",\"body\":\"Great market!\",\"parentEntityType\":\"event\","
                + "\"parentEntityId\":\"evt-1\",\"userAddress\":\"0xabc\",\"createdAt\":\"2024-01-01T00:00:00Z\"}]");

        List<GammaComment> comments = client.comments(
                CommentsRequest.builder().parentEntityType(ParentEntityType.EVENT).parentEntityId("evt-1").build());

        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("c-1", comments.get(0).id());
        assertEquals("Great market!", comments.get(0).body());
    }

    // -----------------------------------------------------------------------
    // TC-GC-017: publicProfile()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-017: publicProfile() deserializes profile")
    void testPublicProfile() throws IOException {
        enqueue("{\"proxyWallet\":\"0xproxy\",\"name\":\"Alice\",\"pseudonym\":\"alice123\","
                + "\"displayUsernamePublic\":true,\"verifiedBadge\":false}");

        GammaPublicProfile profile = client.publicProfile(
                PublicProfileRequest.builder().address("0xproxy").build());

        assertNotNull(profile);
        assertEquals("0xproxy", profile.proxyWallet());
        assertEquals("Alice", profile.name());
        assertTrue(profile.displayUsernamePublic());
    }

    // -----------------------------------------------------------------------
    // TC-GC-018: search()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-018: search() deserializes SearchResults with events, tags, profiles")
    void testSearch() throws IOException {
        enqueue("{\"events\":[{\"id\":\"e1\",\"title\":\"Election 2024\"}],"
                + "\"tags\":[{\"id\":\"t1\",\"label\":\"Politics\",\"slug\":\"politics\",\"eventCount\":5}],"
                + "\"profiles\":[{\"id\":\"p1\",\"name\":\"Bob\",\"pseudonym\":\"bob42\"}],"
                + "\"pagination\":{\"hasMore\":false,\"totalResults\":1}}");

        GammaSearchResults results = client.search(SearchRequest.builder().q("election").build());

        assertNotNull(results);
        assertEquals(1, results.events().size());
        assertEquals("e1", results.events().get(0).id());
        assertEquals(1, results.tags().size());
        assertEquals("Politics", results.tags().get(0).label());
        assertEquals(1, results.profiles().size());
        assertEquals("Bob", results.profiles().get(0).name());
        assertFalse(results.pagination().hasMore());
    }

    // -----------------------------------------------------------------------
    // TC-GC-019: EventsRequest.toQueryParams() — active=true, limit=10
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-019: EventsRequest.toQueryParams() serializes active=true, limit=10 correctly")
    void testEventsRequestToQueryParams() {
        EventsRequest req = EventsRequest.builder().limit(10).active(true).build();
        Map<String, String> params = toSingleMap(req.toQueryParams());

        assertEquals("10", params.get("limit"));
        assertEquals("true", params.get("active"));
        assertFalse(params.containsKey("offset"));
        assertFalse(params.containsKey("closed"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-020: MarketsRequest.toQueryParams() — clobTokenIds repeated params
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-020: MarketsRequest.toQueryParams() serializes clobTokenIds as repeated params")
    void testMarketsRequestToQueryParams() {
        MarketsRequest req = MarketsRequest.builder()
                .clobTokenIds(List.of("tok-1", "tok-2", "tok-3"))
                .build();
        Map<String, List<String>> mm = toMultiMap(req.toQueryParams());

        assertEquals(3, mm.get("clob_token_ids").size());
        assertTrue(mm.get("clob_token_ids").contains("tok-1"));
        assertTrue(mm.get("clob_token_ids").contains("tok-2"));
        assertTrue(mm.get("clob_token_ids").contains("tok-3"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-021: EventsRequest.toQueryParams() omits null fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-021: EventsRequest.toQueryParams() omits null fields")
    void testEventsRequestOmitsNulls() {
        EventsRequest req = EventsRequest.builder().build();
        List<Map.Entry<String, String>> params = req.toQueryParams();

        assertTrue(params.isEmpty());
    }

    // -----------------------------------------------------------------------
    // TC-GC-026: tagsRelatedToTagById()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-026: tagsRelatedToTagById() returns tag list")
    void testTagsRelatedToTagById() throws IOException {
        enqueue("[{\"id\":\"1\",\"label\":\"Sports\",\"slug\":\"sports\"}]");

        List<GammaTag> tags = client.tagsRelatedToTagById(RelatedTagsByIdRequest.builder().id("42").build());

        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertEquals("1", tags.get(0).id());
        assertEquals("Sports", tags.get(0).label());
        assertEquals("sports", tags.get(0).slug());
    }

    // -----------------------------------------------------------------------
    // TC-GC-027: tagsRelatedToTagBySlug()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-027: tagsRelatedToTagBySlug() returns tag list")
    void testTagsRelatedToTagBySlug() throws IOException {
        enqueue("[{\"id\":\"7\",\"label\":\"Politics\",\"slug\":\"politics\"}]");

        List<GammaTag> tags = client.tagsRelatedToTagBySlug(RelatedTagsBySlugRequest.builder().slug("politics").build());

        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertEquals("7", tags.get(0).id());
    }

    // -----------------------------------------------------------------------
    // TC-GC-028: relatedTagsBySlug()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-028: relatedTagsBySlug() returns related tags list")
    void testRelatedTagsBySlug() throws IOException {
        enqueue("[{\"tagId\":\"1\",\"relatedTagId\":\"2\"}]");

        List<GammaRelatedTag> related = client.relatedTagsBySlug(RelatedTagsBySlugRequest.builder().slug("crypto").build());

        assertNotNull(related);
        assertEquals(1, related.size());
        assertEquals("1", related.get(0).tagId());
        assertEquals("2", related.get(0).relatedTagId());
    }

    // -----------------------------------------------------------------------
    // TC-GC-029: commentsByUserAddress()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-029: commentsByUserAddress() returns comment list")
    void testCommentsByUserAddress() throws IOException {
        enqueue("[{\"id\":\"c-1\",\"body\":\"Hello\",\"userAddress\":\"0x56687bf447db6ffa42ffe2204a05edaa20f55839\","
                + "\"createdAt\":\"2024-01-01T00:00:00Z\"}]");

        List<GammaComment> comments = client.commentsByUserAddress(
                CommentsByUserAddressRequest.builder().userAddress("0x56687bf447db6ffa42ffe2204a05edaa20f55839").build());

        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("c-1", comments.get(0).id());
        assertEquals("Hello", comments.get(0).body());
    }

    // -----------------------------------------------------------------------
    // TC-GC-030: markets() with empty request
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-030: markets() with empty request returns empty list")
    void testMarketsEmpty() throws IOException {
        enqueue("[]");

        List<GammaMarketDetail> markets = client.markets(MarketsRequest.builder().build());

        assertNotNull(markets);
        assertTrue(markets.isEmpty());
    }

    // -----------------------------------------------------------------------
    // TC-GC-031: markets() with clobTokenIds as repeated query params
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-031: markets() sends clobTokenIds as repeated query params")
    void testMarketsRepeatedQueryParams() throws IOException, InterruptedException {
        enqueue("[{\"id\":\"m1\",\"question\":\"Q?\"}]");

        MarketsRequest req = MarketsRequest.builder().clobTokenIds(List.of("tok1", "tok2")).build();
        client.markets(req);

        RecordedRequest recorded = server.takeRequest();
        HttpUrl url = recorded.getRequestUrl();
        assertNotNull(url);
        assertEquals(List.of("tok1", "tok2"), url.queryParameterValues("clob_token_ids"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-032: EventsRequest.toQueryParams() all params
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-032: EventsRequest.toQueryParams() — id list produces repeated entries")
    void testEventsRequestAllParams() {
        EventsRequest req = EventsRequest.builder()
                .limit(50).offset(10)
                .id(List.of("1", "2", "3"))
                .active(true).ascending(true)
                .build();
        Map<String, List<String>> mm = toMultiMap(req.toQueryParams());

        assertEquals(3, mm.get("id").size());
        assertTrue(mm.get("id").contains("1"));
        assertTrue(mm.get("id").contains("2"));
        assertTrue(mm.get("id").contains("3"));
        assertEquals("50", mm.get("limit").get(0));
        assertEquals("true", mm.get("active").get(0));
    }

    // -----------------------------------------------------------------------
    // TC-GC-033: MarketsRequest.toQueryParams() — clobTokenIds repeated
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-033: MarketsRequest.toQueryParams() — clobTokenIds produces 2 separate entries")
    void testMarketsRequestClobTokenIdsRepeated() {
        MarketsRequest req = MarketsRequest.builder()
                .clobTokenIds(List.of("a", "b"))
                .build();
        List<Map.Entry<String, String>> params = req.toQueryParams();
        long count = params.stream().filter(e -> e.getKey().equals("clob_token_ids")).count();

        assertEquals(2, count);
    }

    // -----------------------------------------------------------------------
    // TC-GC-034: Empty arrays not included
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-034: Empty arrays produce no entries in toQueryParams()")
    void testEmptyArraysNotIncluded() {
        EventsRequest evtReq = EventsRequest.builder().id(List.of()).build();
        Map<String, List<String>> evtMm = toMultiMap(evtReq.toQueryParams());
        assertNull(evtMm.get("id"));

        MarketsRequest mktReq = MarketsRequest.builder().clobTokenIds(List.of()).build();
        Map<String, List<String>> mktMm = toMultiMap(mktReq.toQueryParams());
        assertNull(mktMm.get("clob_token_ids"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-035: SeriesListRequest.toQueryParams() — categoriesIds/Labels repeated
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-035: SeriesListRequest.toQueryParams() — categoriesIds and categoriesLabels produce repeated entries")
    void testSeriesListRequestCategoriesRepeated() {
        SeriesListRequest req = SeriesListRequest.builder()
                .categoriesIds(List.of("1", "2", "3"))
                .categoriesLabels(List.of("Sports", "Politics"))
                .build();
        Map<String, List<String>> mm = toMultiMap(req.toQueryParams());

        assertEquals(3, mm.get("categories_ids").size());
        assertTrue(mm.get("categories_ids").contains("1"));
        assertTrue(mm.get("categories_ids").contains("2"));
        assertTrue(mm.get("categories_ids").contains("3"));
        assertEquals(2, mm.get("categories_labels").size());
        assertTrue(mm.get("categories_labels").contains("Sports"));
        assertTrue(mm.get("categories_labels").contains("Politics"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-036: RelatedTagsByIdRequest.toQueryParams()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-036: RelatedTagsByIdRequest.toQueryParams() — omitEmpty and status serialized correctly")
    void testRelatedTagsByIdRequestParams() {
        RelatedTagsByIdRequest req = RelatedTagsByIdRequest.builder()
                .id("42").omitEmpty(true).status(RelatedTagsStatus.ACTIVE).build();
        Map<String, String> params = toSingleMap(req.toQueryParams());

        assertEquals("true", params.get("omit_empty"));
        assertEquals("active", params.get("status"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-037: CommentsRequest.toQueryParams()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-037: CommentsRequest.toQueryParams() — parentEntityType enum serialized correctly")
    void testCommentsRequestParentEntityType() {
        CommentsRequest eventReq = CommentsRequest.builder().parentEntityType(ParentEntityType.EVENT).build();
        Map<String, String> eventParams = toSingleMap(eventReq.toQueryParams());
        assertEquals("Event", eventParams.get("parent_entity_type"));

        CommentsRequest marketReq = CommentsRequest.builder().parentEntityType(ParentEntityType.MARKET).build();
        Map<String, String> marketParams = toSingleMap(marketReq.toQueryParams());
        assertEquals("market", marketParams.get("parent_entity_type"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-038: SearchRequest.toQueryParams()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-038: SearchRequest.toQueryParams() — eventsTags uses singular key; keepClosedMarkets is Integer")
    void testSearchRequestParams() {
        SearchRequest req = SearchRequest.builder()
                .eventsTags(List.of("crypto", "finance"))
                .keepClosedMarkets(5)
                .build();
        Map<String, List<String>> mm = toMultiMap(req.toQueryParams());

        assertEquals(2, mm.get("events_tag").size());
        assertTrue(mm.get("events_tag").contains("crypto"));
        assertTrue(mm.get("events_tag").contains("finance"));
        assertEquals("5", mm.get("keep_closed_markets").get(0));
    }

    // -----------------------------------------------------------------------
    // TC-GC-039: TeamsRequest.toQueryParams()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-039: TeamsRequest.toQueryParams() — league list produces repeated entries")
    void testTeamsRequestRepeated() {
        TeamsRequest req = TeamsRequest.builder()
                .league(List.of("NBA", "NFL"))
                .build();
        Map<String, List<String>> mm = toMultiMap(req.toQueryParams());

        assertEquals(2, mm.get("league").size());
        assertTrue(mm.get("league").contains("NBA"));
        assertTrue(mm.get("league").contains("NFL"));

        TeamsRequest emptyReq = TeamsRequest.builder().league(List.of()).build();
        Map<String, List<String>> emptyMm = toMultiMap(emptyReq.toQueryParams());
        assertNull(emptyMm.get("league"));
    }

    @Test
    @DisplayName("TC-GC-040: SeriesListRequest.toQueryParams() — exclude_events present when set, absent when omitted")
    void testSeriesListRequestExcludeEvents() {
        Map<String, String> withFlag = toSingleMap(
                SeriesListRequest.builder().excludeEvents(true).build().toQueryParams());
        assertEquals("true", withFlag.get("exclude_events"));

        Map<String, String> withoutFlag = toSingleMap(
                SeriesListRequest.builder().build().toQueryParams());
        assertNull(withoutFlag.get("exclude_events"));
    }

    @Test
    @DisplayName("TC-GC-041: EventsRequest — after_cursor present when set, absent when omitted")
    void testEventsRequestAfterCursor() {
        Map<String, String> with = toSingleMap(
                EventsRequest.builder().afterCursor("LTE=").build().toQueryParams());
        assertEquals("LTE=", with.get("after_cursor"));

        Map<String, String> without = toSingleMap(EventsRequest.builder().build().toQueryParams());
        assertNull(without.get("after_cursor"));
    }

    @Test
    @DisplayName("TC-GC-042: eventsKeyset() hits /events/keyset, parses events + next_cursor")
    void testEventsKeyset() throws IOException, InterruptedException {
        enqueue("{\"events\":[{\"id\":\"evt-1\",\"slug\":\"e1\"},{\"id\":\"evt-2\",\"slug\":\"e2\"}],\"next_cursor\":\"LTI=\"}");

        EventsKeysetResponse resp = client.eventsKeyset(
                EventsRequest.builder().limit(2).afterCursor("LTE=").build());

        assertEquals(2, resp.getEvents().size());
        assertEquals("evt-1", resp.getEvents().get(0).id());
        assertEquals("LTI=", resp.getNextCursor());

        HttpUrl url = server.takeRequest().getRequestUrl();
        assertEquals("/events/keyset", url.encodedPath());
        assertEquals("LTE=", url.queryParameter("after_cursor"));
    }

    @Test
    @DisplayName("TC-GC-043: eventsKeyset() last page omits next_cursor -> null")
    void testEventsKeysetLastPage() throws IOException {
        enqueue("{\"events\":[]}");
        EventsKeysetResponse resp = client.eventsKeyset(EventsRequest.builder().build());
        assertTrue(resp.getEvents().isEmpty());
        assertNull(resp.getNextCursor());
    }

    @Test
    @DisplayName("TC-GC-044: marketsKeyset() hits /markets/keyset, parses markets + next_cursor")
    void testMarketsKeyset() throws IOException, InterruptedException {
        enqueue("{\"markets\":[{\"id\":\"m1\",\"question\":\"Q?\"}],\"next_cursor\":\"LTI=\"}");

        MarketsKeysetResponse resp = client.marketsKeyset(
                MarketsRequest.builder().limit(1).afterCursor("LTE=").build());

        assertEquals(1, resp.getMarkets().size());
        assertEquals("m1", resp.getMarkets().get(0).id());
        assertEquals("LTI=", resp.getNextCursor());
        assertEquals("/markets/keyset", server.takeRequest().getRequestUrl().encodedPath());
    }

    // -----------------------------------------------------------------------
    // TC-GC-022: teams()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-022: teams(TeamsRequest) returns team list")
    void testTeams() throws IOException {
        enqueue("[{\"id\":\"tm-1\",\"name\":\"Lakers\",\"league\":\"NBA\",\"abbreviation\":\"LAL\"}]");

        List<GammaTeam> teams = client.teams(TeamsRequest.builder().limit(10).build());

        assertNotNull(teams);
        assertEquals(1, teams.size());
        assertEquals("Lakers", teams.get(0).name());
        assertEquals("NBA", teams.get(0).league());
    }

    // -----------------------------------------------------------------------
    // TC-GC-023: sports()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-023: sports() returns sports metadata list")
    void testSports() throws IOException {
        enqueue("[{\"id\":\"sp-1\",\"sport\":\"basketball\",\"image\":\"https://img/bball.png\"}]");

        List<GammaSportsMetadata> sports = client.sports();

        assertNotNull(sports);
        assertEquals(1, sports.size());
        assertEquals("basketball", sports.get(0).sport());
    }

    // -----------------------------------------------------------------------
    // TC-GC-024: sportsMarketTypes()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-024: sportsMarketTypes() returns market types")
    void testSportsMarketTypes() throws IOException {
        enqueue("{\"marketTypes\":[\"winner\",\"spread\",\"over_under\"]}");

        GammaSportsMarketTypesResponse response = client.sportsMarketTypes();

        assertNotNull(response);
        assertEquals(3, response.marketTypes().size());
        assertTrue(response.marketTypes().contains("winner"));
        assertTrue(response.marketTypes().contains("spread"));
    }

    // -----------------------------------------------------------------------
    // TC-GC-025: GammaMarketDetail embedded JSON deserialization
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-GC-025: GammaMarketDetail correctly deserializes embedded JSON outcomes and outcomePrices")
    void testMarketDetailEmbeddedJson() throws IOException {
        enqueue("{\"id\":\"mkt-emb\",\"question\":\"Embedded test?\","
                + "\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\","
                + "\"outcomePrices\":\"[\\\"0.75\\\",\\\"0.25\\\"]\","
                + "\"clobTokenIds\":\"[\\\"clob-a\\\",\\\"clob-b\\\"]\"}");

        GammaMarketDetail market = client.marketById(MarketByIdRequest.builder().id("mkt-emb").build());

        assertNotNull(market);
        assertEquals(List.of("Yes", "No"), market.outcomes());
        assertEquals(List.of("0.75", "0.25"), market.outcomePrices());
        assertEquals(List.of("clob-a", "clob-b"), market.clobTokenIds());
    }
}

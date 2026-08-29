package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.social.SocialGateway;
import com.polymarket.social.Comment;
import com.polymarket.social.CommentPage;
import com.polymarket.social.CommentQuery;
import com.polymarket.social.ParentEntityType;
import com.polymarket.social.Profile;
import com.polymarket.social.Reaction;
import com.polymarket.social.SearchQuery;
import com.polymarket.social.Social;
import com.polymarket.social.SocialSearchResults;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Social")
class SocialTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    /** Credential-free: every Social read must work with no signing authority at all. */
    private Social social() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults().gammaHost(host);
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        return new Social(new SocialGateway(config, runtime));
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    @Test
    @DisplayName("TC-SO-001: a public profile carries its identity, bio and linked accounts")
    void publicProfileCarriesIdentityAndLinkedAccounts() throws Exception {
        enqueue("""
                {"createdAt":"2024-01-01T00:00:00Z","proxyWallet":"0xProxy","profileImage":"https://img/1.png",
                 "displayUsernamePublic":true,"bio":"trader","pseudonym":"alice123","name":"Alice",
                 "xUsername":"alice_x","verifiedBadge":true,
                 "users":[{"id":"7","creator":true,"mod":false}]}""");

        Profile profile = social().profile("0xProxy").orElseThrow();

        assertEquals("0xProxy", profile.proxyWallet().orElseThrow());
        assertEquals("Alice", profile.name().orElseThrow());
        assertEquals("alice123", profile.pseudonym().orElseThrow());
        assertEquals("trader", profile.bio().orElseThrow());
        assertEquals("https://img/1.png", profile.profileImage().orElseThrow());
        assertEquals(true, profile.displayUsernamePublic().orElseThrow());
        assertEquals(true, profile.verifiedBadge().orElseThrow());
        assertEquals("alice_x", profile.xUsername().orElseThrow());
        assertEquals(java.time.Instant.parse("2024-01-01T00:00:00Z"), profile.createdAt().orElseThrow());
        assertEquals("7", profile.users().get(0).id());
        assertEquals(true, profile.users().get(0).creator().orElseThrow());
        assertEquals(false, profile.users().get(0).moderator().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/public-profile?address=0xProxy", request.getPath());
    }

    @Test
    @DisplayName("TC-SO-002: a profile read is empty when Gamma does not know the address")
    void profileIsOptional() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertEquals(Optional.empty(), social().profile("0xUnknown"));

        assertEquals("/public-profile?address=0xUnknown", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-003: a comment carries its author, positions and reactions typed")
    void commentsCarryAuthorPositionsAndReactions() throws Exception {
        enqueue("""
                [{"id":"c-1","body":"Great market!","parentEntityType":"Event","parentEntityId":"evt-1",
                  "parentCommentId":"c-0","userAddress":"0xUser","replyAddress":"0xReply",
                  "createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-02T00:00:00Z",
                  "reportCount":0,"reactionCount":2,
                  "profile":{"name":"Alice","pseudonym":"alice123","displayUsernamePublic":true,
                    "bio":"trader","isMod":false,"isCreator":true,"proxyWallet":"0xProxy",
                    "baseAddress":"0xBase","profileImage":"https://img/1.png",
                    "positions":[{"tokenId":"1343","positionSize":12.5}]},
                  "reactions":[{"id":"r-1","commentId":"c-1","reactionType":"LIKE","icon":"like",
                    "userAddress":"0xReactor","createdAt":"2024-01-01T01:00:00Z"}]}]""");

        List<Comment> comments = social().comments(CommentQuery.limit(20)
                .forEntity(ParentEntityType.EVENT, "evt-1").offset(5).order("createdAt")
                .ascending(false).includePositions(true).holdersOnly(true));

        Comment comment = comments.get(0);
        assertEquals("c-1", comment.id());
        assertEquals("Great market!", comment.body().orElseThrow());
        assertEquals(ParentEntityType.EVENT, comment.parentEntityType().orElseThrow());
        assertEquals("evt-1", comment.parentEntityId().orElseThrow());
        assertEquals("c-0", comment.parentCommentId().orElseThrow());
        assertEquals("0xUser", comment.userAddress().orElseThrow());
        assertEquals("0xReply", comment.replyAddress().orElseThrow());
        assertEquals(java.time.Instant.parse("2024-01-01T00:00:00Z"), comment.createdAt().orElseThrow());
        assertEquals(java.time.Instant.parse("2024-01-02T00:00:00Z"), comment.updatedAt().orElseThrow());
        assertEquals(0, comment.reportCount().orElseThrow());
        assertEquals(2, comment.reactionCount().orElseThrow());

        var author = comment.author().orElseThrow();
        assertEquals("Alice", author.name().orElseThrow());
        assertEquals(false, author.moderator().orElseThrow());
        assertEquals(true, author.creator().orElseThrow());
        assertEquals("0xProxy", author.proxyWallet().orElseThrow());
        assertEquals("1343", author.positions().get(0).tokenId().orElseThrow());
        assertEquals(new BigDecimal("12.5"), author.positions().get(0).positionSize().orElseThrow());

        assertEquals("r-1", comment.reactions().get(0).id());
        assertEquals("LIKE", comment.reactions().get(0).reactionType().orElseThrow());

        assertEquals("/comments?limit=20&offset=5&order=createdAt&ascending=false"
                        + "&parent_entity_type=Event&parent_entity_id=evt-1"
                        + "&get_positions=true&holders_only=true",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-004: a comment query rejects a non-positive limit before any request is sent")
    void commentQueryRejectsNonPositiveLimit() throws Exception {
        assertThrowsIllegalArgument(() -> CommentQuery.limit(0));
        assertThrowsIllegalArgument(() -> CommentQuery.limit(-1));
        assertEquals(0, server.getRequestCount());
    }

    private static void assertThrowsIllegalArgument(Runnable action) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, action::run);
    }

    @Test
    @DisplayName("TC-SO-005: a comment lookup by id tolerates Gamma's mixed-case parent fields")
    void commentsByIdToleratesMixedCaseParentFields() throws Exception {
        enqueue("""
                [{"id":"c1","body":"hi","parentEntityType":"Event","parentEntityID":"18396",
                  "parentCommentID":"42","userAddress":"0xUser"}]""");

        List<Comment> comments = social().commentsById("c1", true);

        Comment comment = comments.get(0);
        assertEquals("18396", comment.parentEntityId().orElseThrow());
        assertEquals("42", comment.parentCommentId().orElseThrow());
        assertEquals("0xUser", comment.userAddress().orElseThrow());

        assertEquals("/comments/c1?get_positions=true", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-006: a comment lookup by id without positions sends no flag")
    void commentsByIdWithoutPositionsSendsNoFlag() throws Exception {
        enqueue("[{\"id\":\"c1\",\"userAddress\":\"0xUser\"}]");

        social().commentsById("c1");

        assertEquals("/comments/c1", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-007: comments by user address send every sort filter and default to unpaged")
    void commentsByUserAddressSendEverySortFilter() throws Exception {
        enqueue("""
                [{"id":"c-1","body":"Hello","userAddress":"0x5668","createdAt":"2024-01-01T00:00:00Z"}]""");

        List<Comment> comments = social().commentsByUserAddress("0x5668",
                CommentPage.limit(10).offset(20).order("createdAt").ascending(true));

        assertEquals("c-1", comments.get(0).id());
        assertEquals("/comments/user_address/0x5668?limit=10&offset=20&order=createdAt&ascending=true",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-008: a comment page rejects a non-positive limit before any request is sent")
    void commentPageRejectsNonPositiveLimit() throws Exception {
        assertThrowsIllegalArgument(() -> CommentPage.limit(0));
        assertThrowsIllegalArgument(() -> CommentPage.limit(-5));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-SO-009: a profile search returns typed hits and its pagination signal")
    void searchReturnsTypedProfilesAndPagination() throws Exception {
        enqueue("""
                {"events":[{"id":"e1","title":"Election 2024"}],
                 "tags":[{"id":"t1","label":"Politics"}],
                 "profiles":[{"id":"p1","name":"Bob","pseudonym":"bob42",
                   "displayUsernamePublic":true,"profileImage":"https://img/bob.png","bio":"trader",
                   "proxyWallet":"0xBob","walletActivated":true,"isCloseOnly":false}],
                 "pagination":{"hasMore":true,"totalResults":42}}""");

        SocialSearchResults results = social().search(
                SearchQuery.of("election").limitPerType(5).page(2));

        var profile = results.profiles().get(0);
        assertEquals("p1", profile.id());
        assertEquals("Bob", profile.name().orElseThrow());
        assertEquals("bob42", profile.pseudonym().orElseThrow());
        assertEquals(true, profile.displayUsernamePublic().orElseThrow());
        assertEquals("https://img/bob.png", profile.profileImage().orElseThrow());
        assertEquals("trader", profile.bio().orElseThrow());
        assertEquals("0xBob", profile.proxyWallet().orElseThrow());
        assertEquals(true, profile.walletActivated().orElseThrow());
        assertEquals(false, profile.closeOnly().orElseThrow());
        assertEquals(true, results.hasMore().orElseThrow());
        assertEquals(42, results.totalResults().orElseThrow());

        assertEquals("/public-search?q=election&search_profiles=true&limit_per_type=5&page=2",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-SO-010: a blank search query is rejected before any request is sent")
    void searchRejectsBlankQuery() throws Exception {
        assertThrowsIllegalArgument(() -> SearchQuery.of(" "));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-SO-011: an omitted or null field stays absent instead of becoming a value")
    void absentValuesAreNotFabricated() throws Exception {
        enqueue("{}");
        enqueue("[{\"id\":\"c-1\"}]");

        Social social = social();
        Profile profile = social.profile("0xAddr").orElseThrow();
        assertEquals(Optional.empty(), profile.name());
        assertEquals(Optional.empty(), profile.proxyWallet());
        assertEquals(Optional.empty(), profile.createdAt());
        assertEquals(List.of(), profile.users());

        Comment comment = social.commentsById("c-1").get(0);
        assertEquals("c-1", comment.id());
        assertEquals(Optional.empty(), comment.body());
        assertEquals(Optional.empty(), comment.parentEntityType());
        assertEquals(Optional.empty(), comment.author());
        assertEquals(List.of(), comment.reactions());
    }

    @Test
    @DisplayName("TC-SO-012: unknown response fields are tolerated and never surface as raw maps")
    void unknownFieldsAreToleratedWithoutRawMaps() throws Exception {
        enqueue("[{\"id\":\"c-1\",\"body\":\"hi\",\"aFieldPolymarketAddedYesterday\":{\"nested\":[1,2]}}]");

        Comment comment = social().commentsById("c-1").get(0);
        assertEquals("hi", comment.body().orElseThrow());

        Set<Class<?>> visited = new LinkedHashSet<>();
        assertNoRawContainers(Comment.class, visited);
        assertNoRawContainers(Profile.class, visited);
        assertNoRawContainers(SocialSearchResults.class, visited);
    }

    @Test
    @DisplayName("TC-SO-014: a parent entity type Gamma has not documented yet stays absent")
    void unrecognisedParentEntityTypeStaysAbsentRatherThanFailing() throws Exception {
        enqueue("[{\"id\":\"c-1\",\"parentEntityType\":\"SomeFutureKind\"}]");

        Comment comment = social().commentsById("c-1").get(0);

        assertEquals(Optional.empty(), comment.parentEntityType());
    }

    @Test
    @DisplayName("TC-SO-013: a blank address, id or user address is rejected before any request is sent")
    void blankIdentifiersAreRejected() throws Exception {
        Social social = social();
        assertThrows(IllegalArgumentException.class, () -> social.profile(" "));
        assertThrows(IllegalArgumentException.class, () -> social.commentsById(" "));
        assertThrows(IllegalArgumentException.class,
                () -> social.commentsByUserAddress(" ", CommentPage.limit(10)));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-SO-015: a reaction maps its documented identifiers from Gamma's own casing")
    void reactionIdentifiersMapFromGammasOwnFieldCasing() throws Exception {
        // The documented reaction payload: id, commentID, reactionType, icon, userAddress, createdAt.
        enqueue("""
                [{"id":"c-1","reactions":[{"id":"8675309","commentID":1763355,
                  "reactionType":"HEART","icon":"❤️",
                  "userAddress":"0xce533188d53a16ed580fd5121dedf166d3482677",
                  "createdAt":"2025-07-25T14:50:04.120000Z"}]}]""");

        Reaction reaction = social().commentsById("c-1").get(0).reactions().get(0);

        assertEquals("8675309", reaction.id());
        assertEquals("1763355", reaction.commentId().orElseThrow());
        assertEquals("HEART", reaction.reactionType().orElseThrow());
        assertEquals("❤️", reaction.icon().orElseThrow());
        assertEquals("0xce533188d53a16ed580fd5121dedf166d3482677",
                reaction.userAddress().orElseThrow());
        assertEquals(java.time.Instant.parse("2025-07-25T14:50:04.120000Z"),
                reaction.createdAt().orElseThrow());
    }

    @Test
    @DisplayName("TC-SO-016: a reaction's nested profile is preserved when present and absent when not")
    void nestedReactionProfileIsPreservedWhenPresent() throws Exception {
        enqueue("""
                [{"id":"c-1","reactions":[
                  {"id":"r-1","commentID":1763355,"reactionType":"HEART",
                   "profile":{"name":"salted.caramel","pseudonym":"Adored-Disparity",
                     "displayUsernamePublic":true,
                     "baseAddress":"0xce533188d53a16ed580fd5121dedf166d3482677",
                     "proxyWallet":"0x4ca749dcfa93c87e5ee23e2d21ff4422c7a4c1ee"}},
                  {"id":"r-2","commentID":1763355,"reactionType":"HEART"}]}]""");

        List<Reaction> reactions = social().commentsById("c-1").get(0).reactions();

        var author = reactions.get(0).author().orElseThrow();
        assertEquals("salted.caramel", author.name().orElseThrow());
        assertEquals("Adored-Disparity", author.pseudonym().orElseThrow());
        assertEquals(true, author.displayUsernamePublic().orElseThrow());
        assertEquals("0xce533188d53a16ed580fd5121dedf166d3482677",
                author.baseAddress().orElseThrow());
        assertEquals("0x4ca749dcfa93c87e5ee23e2d21ff4422c7a4c1ee",
                author.proxyWallet().orElseThrow());

        assertEquals(Optional.empty(), reactions.get(1).author());
    }

    @Test
    @DisplayName("TC-SO-017: a social read whose identity is missing or blank fails explicitly")
    void aMissingOrBlankRequiredIdentityFailsTheRead() throws Exception {
        Social social = social();

        enqueue("[{\"body\":\"a comment nobody can name\"}]");
        assertThrows(IOException.class, () -> social.commentsById("c-1"));

        enqueue("[{\"id\":\" \",\"body\":\"a blank id is no id\"}]");
        assertThrows(IOException.class, () -> social.commentsById("c-1"));

        enqueue("[{\"id\":\"c-1\",\"reactions\":[{\"reactionType\":\"HEART\"}]}]");
        assertThrows(IOException.class, () -> social.commentsById("c-1"));

        enqueue("{\"proxyWallet\":\"0xProxy\",\"users\":[{\"creator\":true}]}");
        assertThrows(IOException.class, () -> social.profile("0xProxy"));

        enqueue("{\"profiles\":[{\"name\":\"Bob\"}]}");
        assertThrows(IOException.class, () -> social.search(SearchQuery.of("bob")));
    }

    @Test
    @DisplayName("TC-SO-018: an identified comment keeps every optional field absent, never blank")
    void anIdentifiedCommentKeepsOptionalFieldsAbsent() throws Exception {
        enqueue("[{\"id\":\"c-1\",\"body\":\"\",\"userAddress\":null,\"reactions\":[]}]");

        Comment comment = social().commentsById("c-1").get(0);

        assertEquals("c-1", comment.id());
        assertEquals(Optional.empty(), comment.body());
        assertEquals(Optional.empty(), comment.userAddress());
        assertEquals(Optional.empty(), comment.reactionCount());
        assertEquals(List.of(), comment.reactions());
    }

    @Test
    @DisplayName("TC-SO-019: an undocumented parent entity type stays readable as the text Gamma sent")
    void anUndocumentedParentEntityTypeStaysReadable() throws Exception {
        enqueue("""
                [{"id":"c-1","body":"hi","parentEntityType":"SomeFutureKind",
                  "parentEntityID":18396}]""");

        Comment comment = social().commentsById("c-1").get(0);

        assertEquals(Optional.empty(), comment.parentEntityType());
        assertEquals("SomeFutureKind", comment.parentEntityTypeText().orElseThrow());
        assertEquals("18396", comment.parentEntityId().orElseThrow());
        assertEquals("hi", comment.body().orElseThrow());
    }

    @Test
    @DisplayName("TC-SO-020: every comment read is caller-bounded — no read walks Gamma unbounded")
    void everyCommentReadIsCallerBounded() throws Exception {
        for (Method method : Social.class.getDeclaredMethods()) {
            if (!List.class.isAssignableFrom(method.getReturnType())) continue;
            List<Class<?>> parameters = List.of(method.getParameterTypes());
            assertTrue(
                    parameters.contains(CommentQuery.class) || parameters.contains(CommentPage.class)
                            || method.getName().equals("commentsById"),
                    "Social." + method.getName() + " reads comments without a caller-supplied bound");
        }

        enqueue("[]");
        enqueue("[]");
        enqueue("[]");
        Social social = social();
        social.comments(CommentQuery.limit(20));
        social.commentsByUserAddress("0x5668", CommentPage.limit(10));
        social.commentsById("c-1");

        assertTrue(server.takeRequest().getPath().contains("limit=20"));
        assertTrue(server.takeRequest().getPath().contains("limit=10"));
        assertEquals("/comments/c-1", server.takeRequest().getPath());
    }

    /** Walks every type reachable from the model and rejects escape hatches. */
    private static void assertNoRawContainers(Class<?> type, Set<Class<?>> visited) {
        if (!visited.add(type)) return;
        for (Method method : type.getMethods()) {
            Class<?> returned = method.getReturnType();
            assertFalse(Map.class.isAssignableFrom(returned),
                    type.getSimpleName() + "." + method.getName() + " exposes a raw map");
            assertFalse(returned.getName().startsWith("com.fasterxml"),
                    type.getSimpleName() + "." + method.getName() + " exposes a transport type");
            if (returned.getPackageName().equals(Comment.class.getPackageName())) {
                assertNoRawContainers(returned, visited);
            }
        }
    }
}

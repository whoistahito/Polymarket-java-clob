package com.polymarket.internal.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.social.Comment;
import com.polymarket.social.CommentAuthor;
import com.polymarket.social.CommentPage;
import com.polymarket.social.CommentPosition;
import com.polymarket.social.CommentQuery;
import com.polymarket.social.LinkedAccount;
import com.polymarket.social.ParentEntityType;
import com.polymarket.social.Profile;
import com.polymarket.social.Reaction;
import com.polymarket.social.SearchProfile;
import com.polymarket.social.SearchQuery;
import com.polymarket.social.SocialDirectory;
import com.polymarket.social.SocialSearchResults;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Gamma transport for social reads; maps wire JSON to domain values and nothing else. */
public final class SocialGateway implements SocialDirectory {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final PolymarketConfig config;
    private final HttpRuntime runtime;

    public SocialGateway(PolymarketConfig config, HttpRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
    }

    @Override
    public Optional<Profile> profile(String address) throws IOException {
        Optional<JsonNode> body = readOptional("/public-profile?address=" + encode(address));
        return body.isPresent() ? Optional.of(profile(body.get())) : Optional.empty();
    }

    @Override
    public List<Comment> comments(CommentQuery query) throws IOException {
        QueryString params = new QueryString();
        params.add("limit", String.valueOf(query.limit()));
        query.offset().ifPresent(v -> params.add("offset", v.toString()));
        query.order().ifPresent(v -> params.add("order", v));
        query.ascending().ifPresent(v -> params.add("ascending", v.toString()));
        query.parentEntityType().ifPresent(v -> params.add("parent_entity_type", v.wireValue()));
        query.parentEntityId().ifPresent(v -> params.add("parent_entity_id", v));
        query.includePositions().ifPresent(v -> params.add("get_positions", v.toString()));
        query.holdersOnly().ifPresent(v -> params.add("holders_only", v.toString()));
        return read("/comments" + params);
    }

    @Override
    public List<Comment> commentsById(String id, Optional<Boolean> includePositions)
            throws IOException {
        QueryString params = new QueryString();
        includePositions.ifPresent(v -> params.add("get_positions", v.toString()));
        return read("/comments/" + encode(id) + params);
    }

    @Override
    public List<Comment> commentsByUserAddress(String address, CommentPage page)
            throws IOException {
        QueryString params = new QueryString();
        params.add("limit", String.valueOf(page.limit()));
        page.offset().ifPresent(v -> params.add("offset", v.toString()));
        page.order().ifPresent(v -> params.add("order", v));
        page.ascending().ifPresent(v -> params.add("ascending", v.toString()));
        return read("/comments/user_address/" + encode(address) + params);
    }

    @Override
    public SocialSearchResults search(SearchQuery query) throws IOException {
        QueryString params = new QueryString();
        params.add("q", query.q());
        // Social's own read is the profile side of this endpoint; Markets.search() owns events/tags.
        params.add("search_profiles", "true");
        query.limitPerType().ifPresent(v -> params.add("limit_per_type", v.toString()));
        query.page().ifPresent(v -> params.add("page", v.toString()));

        HttpOutcome outcome = runtime.get(config.gammaHost(), "/public-search" + params, ACCEPT_JSON);
        if (!outcome.successful()) {
            throw new IOException(
                    "social search failed with HTTP " + outcome.status());
        }
        JsonNode body = runtime.parse(outcome.body());
        List<SearchProfile> profiles = new ArrayList<>();
        for (JsonNode node : body.path("profiles")) {
            profiles.add(searchProfile(node));
        }
        JsonNode pagination = body.path("pagination");
        return new SocialSearchResults(profiles, flag(pagination, "hasMore"),
                integer(pagination, "totalResults"));
    }

    private static SearchProfile searchProfile(JsonNode node) throws IOException {
        return new SearchProfile(
                required(node, "id", "profile id"), text(node, "name"), text(node, "pseudonym"),
                flag(node, "displayUsernamePublic"), text(node, "profileImage"), text(node, "bio"),
                text(node, "proxyWallet"), flag(node, "walletActivated"), flag(node, "isCloseOnly"));
    }

    private List<Comment> read(String path) throws IOException {
        HttpOutcome outcome = runtime.get(config.gammaHost(), path, ACCEPT_JSON);
        if (!outcome.successful()) {
            throw new IOException("social read " + path + " failed with HTTP " + outcome.status());
        }
        List<Comment> comments = new ArrayList<>();
        for (JsonNode node : runtime.parse(outcome.body())) {
            comments.add(comment(node));
        }
        return List.copyOf(comments);
    }

    private static Comment comment(JsonNode node) throws IOException {
        Optional<String> parentEntityType = text(node, "parentEntityType");
        return new Comment(
                required(node, "id", "comment id"),
                text(node, "body"),
                parentEntityType.flatMap(SocialGateway::parentEntityType),
                parentEntityType,
                firstText(node, "parentEntityId", "parentEntityID"),
                firstText(node, "parentCommentId", "parentCommentID"),
                text(node, "userAddress"),
                text(node, "replyAddress"),
                instant(node, "createdAt"),
                instant(node, "updatedAt"),
                Optional.ofNullable(node.get("profile")).map(SocialGateway::commentAuthor),
                reactions(node.get("reactions")),
                integer(node, "reportCount"),
                integer(node, "reactionCount"));
    }

    /**
     * Gamma's own enum values, not uniformly cased: "Event", "Series", "market". A value Gamma
     * adds later stays absent here; the comment keeps its wire text so it is still readable.
     */
    private static Optional<ParentEntityType> parentEntityType(String wireValue) {
        for (ParentEntityType type : ParentEntityType.values()) {
            if (type.wireValue().equalsIgnoreCase(wireValue)) return Optional.of(type);
        }
        return Optional.empty();
    }

    private static CommentAuthor commentAuthor(JsonNode node) {
        List<CommentPosition> positions = new ArrayList<>();
        JsonNode array = node.get("positions");
        if (array != null) {
            array.forEach(child -> positions.add(new CommentPosition(
                    text(child, "tokenId"), decimal(child, "positionSize"))));
        }
        return new CommentAuthor(
                text(node, "name"), text(node, "pseudonym"), flag(node, "displayUsernamePublic"),
                text(node, "bio"), flag(node, "isMod"), flag(node, "isCreator"),
                text(node, "proxyWallet"), text(node, "baseAddress"), text(node, "profileImage"),
                positions);
    }

    private static List<Reaction> reactions(JsonNode array) throws IOException {
        if (array == null) return List.of();
        List<Reaction> reactions = new ArrayList<>();
        for (JsonNode node : array) {
            reactions.add(new Reaction(required(node, "id", "reaction id"),
                    firstText(node, "commentID", "commentId"), text(node, "reactionType"),
                    text(node, "icon"), text(node, "userAddress"), instant(node, "createdAt"),
                    Optional.ofNullable(node.get("profile")).map(SocialGateway::commentAuthor)));
        }
        return List.copyOf(reactions);
    }

    private static Profile profile(JsonNode node) throws IOException {
        List<LinkedAccount> users = new ArrayList<>();
        JsonNode array = node.get("users");
        if (array != null) {
            for (JsonNode child : array) {
                users.add(new LinkedAccount(required(child, "id", "linked account id"),
                        flag(child, "creator"), flag(child, "mod")));
            }
        }
        return new Profile(
                text(node, "proxyWallet"), text(node, "name"), text(node, "pseudonym"),
                text(node, "bio"), text(node, "profileImage"),
                flag(node, "displayUsernamePublic"), flag(node, "verifiedBadge"),
                text(node, "xUsername"), instant(node, "createdAt"), users);
    }

    /** A 404 is an answer here, not a failure: Gamma does not know that identifier. */
    private Optional<JsonNode> readOptional(String path) throws IOException {
        HttpOutcome outcome = runtime.get(config.gammaHost(), path, ACCEPT_JSON);
        if (outcome.status() == 404) return Optional.empty();
        if (!outcome.successful()) {
            throw new IOException("social read " + path + " failed with HTTP " + outcome.status());
        }
        return Optional.of(runtime.parse(outcome.body()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Optional<Boolean> flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? Optional.empty() : Optional.of(value.asBoolean());
    }

    private static Optional<Instant> instant(JsonNode node, String field) {
        return text(node, field).map(Instant::parse);
    }

    /** Identity is not optional: a blank or missing id would publish a record nobody can name. */
    private static String required(JsonNode node, String field, String described)
            throws IOException {
        return text(node, field).orElseThrow(
                () -> new IOException("social payload carried no " + described));
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
    }

    /** Gamma sends some comment fields under two casings (e.g. parentEntityId/parentEntityID). */
    private static Optional<String> firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            Optional<String> value = text(node, field);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    /** Keeps the wire text so an exact decimal survives; a JSON number never becomes a double. */
    private static Optional<BigDecimal> decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return Optional.empty();
        return Optional.of(new BigDecimal(value.asText()));
    }

    private static Optional<Integer> integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isIntegralNumber()
                ? Optional.empty() : Optional.of(value.asInt());
    }

    /** Builds the query string in a fixed order so a request is reproducible. */
    private static final class QueryString {
        private final StringBuilder text = new StringBuilder();

        void add(String name, String value) {
            text.append(text.isEmpty() ? '?' : '&').append(name).append('=').append(encode(value));
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}

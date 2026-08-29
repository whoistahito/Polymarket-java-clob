package com.polymarket.social;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;

/** Public profile, comment and search reads. Every call is a credential-free Gamma read. */
public final class Social {

    private final SocialDirectory directory;

    public Social(@NonNull SocialDirectory directory) {
        this.directory = directory;
    }

    /** Empty when Gamma does not know the address. */
    public Optional<Profile> profile(String address) throws IOException {
        return directory.profile(requireNotBlank(address, "address"));
    }

    public List<Comment> comments(@NonNull CommentQuery query) throws IOException {
        return directory.comments(query);
    }

    /**
     * One comment and the replies Gamma nests under it. A thread has no documented ceiling, so the
     * page bound is required here as it is on every other comment read.
     */
    public List<Comment> commentsById(String id, @NonNull CommentPage page) throws IOException {
        return directory.commentsById(requireNotBlank(id, "id"), page, Optional.empty());
    }

    public List<Comment> commentsById(String id, @NonNull CommentPage page,
            boolean includePositions) throws IOException {
        return directory.commentsById(requireNotBlank(id, "id"), page, Optional.of(includePositions));
    }

    public List<Comment> commentsByUserAddress(String address, @NonNull CommentPage page)
            throws IOException {
        return directory.commentsByUserAddress(requireNotBlank(address, "address"), page);
    }

    /** Profile hits for a query; the events/tags side of {@code /public-search} stays Markets.search(). */
    public SocialSearchResults search(@NonNull SearchQuery query) throws IOException {
        return directory.search(query);
    }

    /** Not Lombok's @NonNull: the message names the caller's field, not this parameter. */
    private static String requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

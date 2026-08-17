package com.polymarket.social;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Public profile, comment and search reads. Every call is a credential-free Gamma read. */
public final class Social {

    private final SocialDirectory directory;

    public Social(SocialDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Empty when Gamma does not know the address. */
    public Optional<Profile> profile(String address) throws IOException {
        return directory.profile(requireNotBlank(address, "address"));
    }

    public List<Comment> comments(CommentQuery query) throws IOException {
        return directory.comments(Objects.requireNonNull(query, "query"));
    }

    /** One comment (and its replies, if Gamma nests them) looked up by its own comment id. */
    public List<Comment> commentsById(String id) throws IOException {
        return directory.commentsById(requireNotBlank(id, "id"), Optional.empty());
    }

    public List<Comment> commentsById(String id, boolean includePositions) throws IOException {
        return directory.commentsById(requireNotBlank(id, "id"), Optional.of(includePositions));
    }

    public List<Comment> commentsByUserAddress(String address, CommentPage page) throws IOException {
        return directory.commentsByUserAddress(requireNotBlank(address, "address"),
                Objects.requireNonNull(page, "page"));
    }

    /** Profile hits for a query; the events/tags side of {@code /public-search} stays Markets.search(). */
    public SocialSearchResults search(SearchQuery query) throws IOException {
        return directory.search(Objects.requireNonNull(query, "query"));
    }

    private static String requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

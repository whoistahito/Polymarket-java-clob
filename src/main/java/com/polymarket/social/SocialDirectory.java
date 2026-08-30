package com.polymarket.social;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Port for social reads. The domain declares it; an internal adapter implements it, so no
 * transport type reaches this package.
 */
public interface SocialDirectory {

    Optional<Profile> profile(String address) throws IOException;

    List<Comment> comments(CommentQuery query) throws IOException;

    List<Comment> commentsById(String id, CommentPage page, Optional<Boolean> includePositions) throws IOException;

    List<Comment> commentsByUserAddress(String address, CommentPage page) throws IOException;

    SocialSearchResults search(SearchQuery query) throws IOException;
}

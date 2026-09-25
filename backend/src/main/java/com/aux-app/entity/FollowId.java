package com.aux.entity;

import java.io.Serializable;
import java.util.Objects;

public class FollowId implements Serializable {
    private String followerUserId;
    private String followingUserId;

    public FollowId() {}

    public FollowId(String followerUserId, String followingUserId) {
        this.followerUserId = followerUserId;
        this.followingUserId = followingUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FollowId that)) return false;
        return Objects.equals(followerUserId, that.followerUserId) && Objects.equals(followingUserId, that.followingUserId);
    }

    @Override
    public int hashCode() { return Objects.hash(followerUserId, followingUserId); }
}

package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class FollowEntity {

    @Id
    @Column(name = "follower_user_id")
    private String followerUserId;

    @Id
    @Column(name = "following_user_id")
    private String followingUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected FollowEntity() {}

    public FollowEntity(String followerUserId, String followingUserId) {
        this.followerUserId = followerUserId;
        this.followingUserId = followingUserId;
        this.createdAt = Instant.now();
    }

    public String getFollowerUserId() { return followerUserId; }
    public String getFollowingUserId() { return followingUserId; }
    public Instant getCreatedAt() { return createdAt; }
}

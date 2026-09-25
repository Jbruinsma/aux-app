package com.aux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class TagEntity {

    @Id
    @Column(name = "tag_id")
    private String tagId;

    @Column(nullable = false, unique = true)
    private String tag;

    protected TagEntity() {}

    public TagEntity(String tagId, String tag) {
        this.tagId = tagId;
        this.tag = tag;
    }

    public String getTagId() { return tagId; }
    public String getTag() { return tag; }
}

package com.filmdoms.community.review.data.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.filmdoms.community.review.data.constant.CommentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "\"movie_review_comment\"")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@BatchSize(size = 1000)
public class MovieReviewComment extends BaseTimeEntity {

    @Id @GeneratedValue
    private Long id;

    @JsonIgnore
    @JoinColumn(name = "movie_review_header_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MovieReviewHeader header;

    @JoinColumn(name = "movie_review_comment_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MovieReviewComment parentComment;

    private String content;

    private CommentStatus status = CommentStatus.ACTIVE;

    @Builder
    public MovieReviewComment(MovieReviewHeader header, MovieReviewComment parentComment, String content) {
        this.header = header;
        this.parentComment = parentComment;
        this.content = content;
    }
}
package com.multirkh.chimhahaimage.image.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Links an image to a post. The post lives in the monolith, so {@code postId}
 * is a plain column here — there is no Post entity in this service.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "post_image",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "image_id"})
)
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    public PostImage(Long postId, Image image) {
        this.postId = postId;
        this.image = image;
        image.getPostImages().add(this);
    }
}

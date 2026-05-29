package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.image.domain.Image;
import com.multirkh.chimhahaimage.image.domain.PostImage;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Set<PostImage> findByPostId(Long postId);

    @Modifying
    @Query("DELETE FROM PostImage pi WHERE pi IN :postImages")
    void deleteAllByPostImages(Set<PostImage> postImages);

    /** True if any post still links this image — used to spare shared images from orphan deletion. */
    boolean existsByImage(Image image);
}

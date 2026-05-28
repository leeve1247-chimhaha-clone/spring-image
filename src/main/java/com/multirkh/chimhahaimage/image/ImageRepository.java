package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.image.domain.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Image findByFileName(String fileName);

    Image findBySha256(String sha256);
}

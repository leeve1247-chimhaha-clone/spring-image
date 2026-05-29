package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.image.domain.Image;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Image findByFileName(String fileName);

    Image findBySha256(String sha256);

    @Query("SELECT i FROM Image i WHERE i.fileName IN :fileNames")
    Set<Image> findByFileNames(Set<String> fileNames);

    @Modifying
    @Query("DELETE FROM Image i WHERE i in :images")
    void deleteAllByImages(Set<Image> images);

    @Query("SELECT i FROM Image i WHERE i.editedDate <= :thresholdDate and i.postImages is empty ")
    Set<Image> findImagesEditedBefore(ZonedDateTime thresholdDate);
}

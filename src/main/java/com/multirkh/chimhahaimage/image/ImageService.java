package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.common.minio.MinioService;
import com.multirkh.chimhahaimage.common.util.IdGenerator;
import com.multirkh.chimhahaimage.image.domain.Image;
import com.multirkh.chimhahaimage.image.dtos.PresignedPostDto;
import com.multirkh.chimhahaimage.image.dtos.PresignedUrlDTO;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final ImageRepository imageRepository;
    private final MinioService minioService;

    public PresignedUrlDTO getPresignedUrl() {
        String randomImageName = IdGenerator.generateUniqueId();
        while (imageRepository.findByFileName(randomImageName) != null) {
            randomImageName = IdGenerator.generateUniqueId();
        }
        return new PresignedUrlDTO(minioService.getPresignedUrl(randomImageName), randomImageName);
    }

    public PresignedPostDto getPresignedPost(MimeType mimeType, String sha256) {
        if (sha256 != null && !sha256.isBlank()) {
            Image existing = imageRepository.findBySha256(sha256);
            if (existing != null) {
                // Match the leading-slash convention used by the non-dedup
                // branch (TODO: SeaweedFS 3.02 workaround). Frontend then
                // assembles the src URL identically in both branches.
                return PresignedPostDto.deduped("/" + existing.getFileName());
            }
        }

        String randomImageName = IdGenerator.generateUniqueId();
        while (imageRepository.findByFileName(randomImageName) != null) {
            randomImageName = IdGenerator.generateUniqueId();
        }
        String fileName = String.join(".", randomImageName, mimeType.getSubtype());

        // save image temporary
        imageRepository.save(new Image(fileName, mimeType.getSubtype(),
                String.join("/", minioService.getImageEndPointUrl(), fileName),
                ZonedDateTime.now().plusHours(167),
                sha256));

        return new PresignedPostDto(
                "/" + fileName,
                minioService.getImageEndPointUrl(),
                minioService.getPresignedPost("/" + fileName)
                //TODO: TEMPORARY MEASURE: slash is for seaweedfs 3.02 MUST BE CHANGED WHEN SEAWEEDFS FIXED
        );
    }

    public String getSrcUrl(String fileName) {
        Image image = imageRepository.findByFileName(fileName);
        if (image == null) {
            String srcUrl = minioService.createOrRenewUrl(fileName);
            String contentType = minioService.getType(fileName);
            imageRepository.save(new Image(
                    fileName,
                    contentType,
                    srcUrl,
                    ZonedDateTime.now().plusHours(167)
            ));
            return srcUrl;
        }
        if (image.getExpirationDate().isBefore(ZonedDateTime.now().plusHours(1))) {
            String srcUrl = minioService.createOrRenewUrl(fileName);
            image.setUrl(srcUrl);
        }
        return image.getUrl();
    }

    public String getThumbnailSrcUrl(String fileName) {
        Image rawImage = imageRepository.findByFileName(fileName);
        Image thumbNailImage = getOrCreateThumbnail(rawImage);
        if (thumbNailImage.getExpirationDate().isBefore(ZonedDateTime.now().plusHours(1))) {
            String renewedUrl = minioService.createOrRenewUrl(fileName);
            thumbNailImage.setUrl(renewedUrl);
        }
        return thumbNailImage.getUrl();
    }

    public Image getOrCreateThumbnail(Image rawImage) {
        if (rawImage.getThumbNailImage() != null) {
            return rawImage.getThumbNailImage();
        }
        String srcUrl = minioService.createThumbnail(rawImage.getFileName());
        Image thumbNailImage = new Image(rawImage, srcUrl, ZonedDateTime.now().plusHours(167));
        rawImage.setThumbNailImage(thumbNailImage);
        return imageRepository.save(thumbNailImage);
    }
}

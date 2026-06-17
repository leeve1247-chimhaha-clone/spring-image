package com.multirkh.chimhahaimage.common.minio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the browser-POST form fields (base64 policy + SigV4 signature) for a
 * direct-to-S3 upload. AWS SDK v2 (Java) has no presigned-POST generator, so
 * this reproduces the exact field set MinIO's {@code getPresignedPostFormData}
 * returned — letting the existing frontend FormData upload keep working with no
 * contract change after the MinIO SDK → AWS SDK v2 migration.
 */
@Component
public class S3PostPolicySigner {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final Duration VALIDITY = Duration.ofMinutes(15);
    private static final long MIN_CONTENT_LENGTH = 1L;
    private static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EXPIRATION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final ObjectMapper objectMapper;

    public S3PostPolicySigner(
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.region}") String region,
            ObjectMapper objectMapper) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.objectMapper = objectMapper;
    }

    /**
     * Signs a POST policy constraining the upload to exactly {@code objectKey}
     * in {@code bucket}, valid for 15 minutes, body size 1 byte–10 MiB.
     * Returns the signature form fields; the caller adds the {@code key} field.
     */
    public Map<String, String> presignedPostFields(String bucket, String objectKey) {
        return presignedPostFields(bucket, objectKey, Instant.now());
    }

    // Package-private, time-injectable overload for deterministic testing.
    Map<String, String> presignedPostFields(String bucket, String objectKey, Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String credential = accessKey + "/" + dateStamp + "/" + region + "/s3/aws4_request";
        String expiration = EXPIRATION.format(now.plus(VALIDITY));

        String policyJson = buildPolicyJson(bucket, objectKey, expiration, amzDate, credential);
        String policyBase64 = Base64.getEncoder()
                .encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signature = sign(dateStamp, policyBase64);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("x-amz-algorithm", ALGORITHM);
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", amzDate);
        fields.put("policy", policyBase64);
        fields.put("x-amz-signature", signature);
        return fields;
    }

    private String buildPolicyJson(String bucket, String objectKey, String expiration,
                                   String amzDate, String credential) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", expiration);
        policy.put("conditions", List.of(
                Map.of("bucket", bucket),
                List.of("eq", "$key", objectKey),
                List.of("content-length-range", MIN_CONTENT_LENGTH, MAX_CONTENT_LENGTH),
                Map.of("x-amz-algorithm", ALGORITHM),
                Map.of("x-amz-credential", credential),
                Map.of("x-amz-date", amzDate)
        ));
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // SigV4 signing-key derivation, then HMAC the base64 policy (POST-policy form).
    private String sign(String dateStamp, String policyBase64) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, "s3");
        byte[] kSigning = hmac(kService, "aws4_request");
        return toHex(hmac(kSigning, policyBase64));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

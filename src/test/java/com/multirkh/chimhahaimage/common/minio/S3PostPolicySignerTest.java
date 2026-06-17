package com.multirkh.chimhahaimage.common.minio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3PostPolicySignerTest {

    // Fixed inputs whose expected POLICY/SIGNATURE were computed independently
    // (PowerShell .NET HMACSHA256) — a non-circular check on the hand-rolled SigV4.
    private static final String ACCESS_KEY = "admin";
    private static final String SECRET_KEY = "key";
    private static final String REGION = "us-east-1";
    private static final String BUCKET = "minio-bucket";
    private static final String OBJECT_KEY = "/test.png";
    private static final Instant FIXED_NOW = Instant.parse("2026-06-18T00:00:00Z");

    private static final String EXPECTED_POLICY_JSON =
            "{\"expiration\":\"2026-06-18T00:15:00Z\",\"conditions\":["
                    + "{\"bucket\":\"minio-bucket\"},"
                    + "[\"eq\",\"$key\",\"/test.png\"],"
                    + "[\"content-length-range\",1,10485760],"
                    + "{\"x-amz-algorithm\":\"AWS4-HMAC-SHA256\"},"
                    + "{\"x-amz-credential\":\"admin/20260618/us-east-1/s3/aws4_request\"},"
                    + "{\"x-amz-date\":\"20260618T000000Z\"}]}";

    private static final String EXPECTED_SIGNATURE =
            "7fd0a7aa448db67cbfb4a1d7af945b06b894ae6111f463223d86713c113038fd";

    private final S3PostPolicySigner signer =
            new S3PostPolicySigner(ACCESS_KEY, SECRET_KEY, REGION, new ObjectMapper());

    @Test
    @DisplayName("presignedPostFields: policy 문서가 예상 조건들을 정확한 순서로 직렬화한다")
    void policyDocumentMatchesExpectedJson() {
        Map<String, String> fields = signer.presignedPostFields(BUCKET, OBJECT_KEY, FIXED_NOW);

        String decoded = new String(
                Base64.getDecoder().decode(fields.get("policy")), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(EXPECTED_POLICY_JSON);
    }

    @Test
    @DisplayName("presignedPostFields: SigV4 서명이 독립 산출 벡터와 일치한다")
    void signatureMatchesIndependentlyComputedVector() {
        Map<String, String> fields = signer.presignedPostFields(BUCKET, OBJECT_KEY, FIXED_NOW);

        assertThat(fields.get("x-amz-signature")).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    @DisplayName("presignedPostFields: form 필드가 MinIO 호환 키 집합을 갖는다")
    void formFieldsCarryMinioCompatibleKeys() {
        Map<String, String> fields = signer.presignedPostFields(BUCKET, OBJECT_KEY, FIXED_NOW);

        assertThat(fields).containsOnlyKeys(
                "x-amz-algorithm", "x-amz-credential", "x-amz-date", "policy", "x-amz-signature");
        assertThat(fields.get("x-amz-algorithm")).isEqualTo("AWS4-HMAC-SHA256");
        assertThat(fields.get("x-amz-credential"))
                .isEqualTo("admin/20260618/us-east-1/s3/aws4_request");
        assertThat(fields.get("x-amz-date")).isEqualTo("20260618T000000Z");
    }
}

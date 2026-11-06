package com.hackhu.hp.global.storage;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** S3 객체 읽기·삭제. 확정 단계에서 쓰는 만큼만 감싼다. */
@Service
public class S3ObjectStore {

    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStore(S3Client s3Client, @Value("${storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * 객체 앞부분만 Range로 읽는다. 매직바이트 확인은 앞 몇 바이트만 필요해서 전체를 내려받지 않는다.
     *
     * <p>객체가 없으면 빈 배열을 준다. 발급만 받고 실제로 업로드하지 않은 경우가 여기 걸린다.
     */
    public byte[] readHead(String objectKey, int byteCount) {
        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .range("bytes=0-" + (byteCount - 1))
                        .build();
        try {
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException e) {
            return new byte[0];
        }
    }

    /**
     * 객체를 지운다.
     *
     * <p>ponytail: 한 번에 최대 20장이라 하나씩 지운다. 더 많아지면 DeleteObjects로 묶는다.
     */
    public void deleteAll(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        }
    }
}

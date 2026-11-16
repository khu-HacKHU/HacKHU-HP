package com.hackhu.hp.domain.gallery.service;

/**
 * S3 객체 키를 클라이언트가 사진을 여는 주소로 바꾼다.
 *
 * <p>조회 주소 방식(CloudFront 서명 URL)이 바뀌어도 조회 API가 영향을 받지 않게 인터페이스로 둔다. 구현은 S3를 호출하지 않아야 한다. S3 장애 때도
 * 조회는 DB만으로 돼야 하기 때문이다 (2-REQUIREMENTS 4절).
 */
public interface PhotoUrlResolver {

    String resolve(String objectKey);
}

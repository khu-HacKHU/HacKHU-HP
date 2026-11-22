package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.ArchivePostCreateRequest;
import com.hackhu.hp.domain.post.dto.ArchivePostDetailResponse;
import com.hackhu.hp.domain.post.dto.ArchivePostPageResponse;
import com.hackhu.hp.domain.post.dto.ArchivePostSummaryResponse;
import com.hackhu.hp.domain.post.dto.ArchivePostUpdateRequest;
import com.hackhu.hp.domain.post.dto.AttachmentDownloadUrlResponse;
import com.hackhu.hp.domain.post.dto.AttachmentUploadUrlRequest;
import com.hackhu.hp.domain.post.dto.AttachmentUploadUrlResponse;
import com.hackhu.hp.domain.post.entity.Attachment;
import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import com.hackhu.hp.domain.post.exception.PostErrorCode;
import com.hackhu.hp.domain.post.repository.AttachmentRepository;
import com.hackhu.hp.domain.post.repository.AttachmentRepository.PostAttachmentCount;
import com.hackhu.hp.domain.post.repository.PostRepository;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.CommonErrorCode;
import com.hackhu.hp.global.storage.PresignedUrlService;
import com.hackhu.hp.global.storage.S3ObjectStore;
import com.hackhu.hp.global.storage.UploadConfirmService;
import com.hackhu.hp.global.storage.UploadPurpose;
import com.hackhu.hp.global.storage.UploadRecord;
import com.hackhu.hp.global.storage.UploadRecordRepository;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 자료게시판 (#55, docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md 4-2-9 ~ 4-2-11).
 *
 * <p>인증 수준과 권한 규칙은 자유게시판과 같다. 클래스의 {@code @PreAuthorize}가 글을 찾기 전에 동아리원인지 확인한다 (5-1-3, DECISIONS.md
 * B7). 첨부 확정은 {@code global/storage}의 {@link UploadConfirmService}가 하고, S3 객체 삭제는 커밋 뒤에 한다
 * (DECISIONS.md B8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasRole('MEMBER')")
public class ArchivePostService {

    private static final BoardType BOARD = BoardType.ARCHIVE;
    private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_EXPIRY = Duration.ofMinutes(10);

    private final PostRepository postRepository;
    private final AttachmentRepository attachmentRepository;
    private final BoardPostSupport support;
    private final UploadConfirmService uploadConfirmService;
    private final UploadRecordRepository uploadRecordRepository;
    private final PresignedUrlService presignedUrlService;
    private final S3ObjectStore objectStore;
    private final AttachmentRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public ArchivePostPageResponse getPosts(int page, int size) {
        Page<Post> posts = support.findPage(BOARD, page, size);
        Map<Long, String> names = support.authorNames(posts.getContent());
        Map<Long, Long> counts = attachmentCounts(posts.map(Post::getId).getContent());
        return ArchivePostPageResponse.from(
                posts.map(post ->
                        ArchivePostSummaryResponse.of(
                                post,
                                names.get(post.getAuthorId()),
                                counts.getOrDefault(post.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public ArchivePostDetailResponse getPost(Long postId) {
        return detail(support.find(BOARD, postId));
    }

    /** 첨부를 먼저 확정하고 글을 만든다. 확정이 실패하면 글을 만들지 않는다 (4-2-9). */
    public ArchivePostDetailResponse create(LoginUser user, ArchivePostCreateRequest request) {
        rejectDuplicates(request.uploadIds());
        List<UploadRecord> uploads = confirm(user, request.uploadIds());

        // 작성자는 요청이 아니라 로그인한 사람이다 (5-1-3, CWE-915).
        Post post =
                postRepository.save(
                        Post.create(BOARD, request.title(), request.content(), user.id()));
        attach(user, post, uploads, 0);
        return detail(post);
    }

    /**
     * 글과 첨부를 한 트랜잭션에서 바꾼다 (DECISIONS.md B10). 글 행을 잠근 뒤 첨부를 세므로 두 수정 요청이 동시에 와도 5개를 넘지 않는다 (5-1-8).
     */
    public ArchivePostDetailResponse update(
            LoginUser user, Long postId, ArchivePostUpdateRequest request) {
        rejectDuplicates(request.attachmentIds());
        rejectDuplicates(request.uploadIds());

        Post post =
                postRepository
                        .findForUpdate(postId, BOARD)
                        .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        support.checkModifiable(user, post);

        List<Attachment> existing = attachmentRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        Map<Long, Attachment> byId =
                existing.stream().collect(Collectors.toMap(Attachment::getId, Function.identity()));
        // 다른 글의 첨부를 옮겨 오지 못하게 한다 (4-2-9).
        if (!byId.keySet().containsAll(request.attachmentIds())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 업로드를 소비하기 전에 센다. 넘으면 업로드는 그대로 남아 다시 쓸 수 있다.
        if (request.attachmentIds().size() + request.uploadIds().size() > Attachment.MAX_PER_POST) {
            throw new BusinessException(PostErrorCode.POST_TOO_MANY_ATTACHMENTS);
        }

        List<UploadRecord> uploads = confirm(user, request.uploadIds());
        if (!uploads.isEmpty()) {
            // 확정의 조건부 갱신(@Modifying clearAutomatically)이 영속성 컨텍스트를 비운다. 앞에서 읽은 글과 첨부는 분리되어
            // 바꿔도 반영되지 않으므로 다시 읽는다. 잠금은 이미 잡고 있어 그 사이에 바뀌지 않는다.
            post =
                    postRepository
                            .findForUpdate(postId, BOARD)
                            .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
            existing = attachmentRepository.findByPostIdOrderByDisplayOrderAsc(postId);
            byId =
                    existing.stream()
                            .collect(Collectors.toMap(Attachment::getId, Function.identity()));
        }

        Set<Long> keep = new HashSet<>(request.attachmentIds());
        List<Attachment> removed =
                existing.stream().filter(a -> !keep.contains(a.getId())).toList();
        attachmentRepository.deleteAll(removed);
        for (int i = 0; i < request.attachmentIds().size(); i++) {
            byId.get(request.attachmentIds().get(i)).reorder(i);
        }
        attach(user, post, uploads, request.attachmentIds().size());

        post.edit(request.title(), request.content());
        postRepository.flush();
        support.logAdminAction(user, post, "수정");
        deleteObjectsAfterCommit(
                post.getId(), removed.stream().map(Attachment::getObjectKey).toList());
        return detail(post);
    }

    /** 첨부 행은 DB가 CASCADE로 지우고, S3 객체는 커밋 뒤에 지운다 (3-2-4). */
    public void delete(LoginUser user, Long postId) {
        Post post = support.findModifiable(BOARD, user, postId);
        List<String> objectKeys =
                attachmentRepository.findByPostIdOrderByDisplayOrderAsc(postId).stream()
                        .map(Attachment::getObjectKey)
                        .toList();
        postRepository.delete(post);
        postRepository.flush();
        support.logAdminAction(user, post, "삭제");
        deleteObjectsAfterCommit(post.getId(), objectKeys);
    }

    /** 첨부 업로드 주소 발급 (4-2-10). 모든 파일을 먼저 검사하고, 하나라도 맞지 않으면 아무것도 발급하지 않는다. 이 단계에서는 글과 연결되지 않는다. */
    public AttachmentUploadUrlResponse issueUploadUrls(
            LoginUser user, AttachmentUploadUrlRequest request) {
        List<String> fileNames = new ArrayList<>();
        for (AttachmentUploadUrlRequest.FileSpec file : request.files()) {
            String fileName = AttachmentFileRules.sanitizeFileName(file.fileName());
            AttachmentFileRules.checkFormatAndSize(fileName, file.contentType(), file.size());
            fileNames.add(fileName);
        }
        rateLimiter.checkUpload(user.id(), request.files().size());

        Instant expiresAt = Instant.now().plus(UPLOAD_EXPIRY);
        List<AttachmentUploadUrlResponse.Upload> uploads = new ArrayList<>();
        for (int i = 0; i < request.files().size(); i++) {
            AttachmentUploadUrlRequest.FileSpec file = request.files().get(i);
            UploadRecord record =
                    uploadRecordRepository.save(
                            UploadRecord.issue(
                                    user.id(),
                                    UploadPurpose.ATTACHMENT,
                                    file.contentType(),
                                    file.size(),
                                    expiresAt,
                                    fileNames.get(i)));
            // 형식과 정확한 크기를 서명에 넣어, 신고와 다른 파일은 S3가 거부한다 (5-1-7 2단계).
            URL uploadUrl =
                    presignedUrlService.presignPut(
                            record.getObjectKey(), file.contentType(), file.size(), UPLOAD_EXPIRY);
            uploads.add(
                    new AttachmentUploadUrlResponse.Upload(record.getId(), uploadUrl, expiresAt));
        }
        return new AttachmentUploadUrlResponse(uploads);
    }

    /** 첨부 다운로드 주소 발급 (4-2-11). 첨부는 글을 거쳐 찾으므로 다른 글의 첨부 ID로는 받을 수 없다 (DECISIONS.md B5). */
    @Transactional(readOnly = true)
    public AttachmentDownloadUrlResponse issueDownloadUrl(
            LoginUser user, Long postId, Long attachmentId) {
        Post post = support.find(BOARD, postId);
        Attachment attachment =
                attachmentRepository
                        .findByIdAndPostId(attachmentId, post.getId())
                        .orElseThrow(() ->
                                new BusinessException(PostErrorCode.POST_ATTACHMENT_NOT_FOUND));
        rateLimiter.checkDownload(user.id());

        URL url =
                presignedUrlService.presignGet(
                        attachment.getObjectKey(),
                        attachment.getContentType(),
                        attachment.getFileName(),
                        DOWNLOAD_EXPIRY);
        log.info(
                "첨부 다운로드 주소 발급 userId={} postId={} attachmentId={}",
                user.id(),
                post.getId(),
                attachment.getId());
        return new AttachmentDownloadUrlResponse(url, Instant.now().plus(DOWNLOAD_EXPIRY));
    }

    private List<UploadRecord> confirm(LoginUser user, List<UUID> uploadIds) {
        if (uploadIds.isEmpty()) {
            return List.of();
        }
        return uploadConfirmService.confirmAttachments(user.id(), uploadIds);
    }

    private void attach(LoginUser user, Post post, List<UploadRecord> uploads, int firstOrder) {
        for (int i = 0; i < uploads.size(); i++) {
            Attachment attachment =
                    attachmentRepository.save(
                            Attachment.from(post.getId(), uploads.get(i), firstOrder + i));
            // 첨부 확정 기록 (5-1-12). 파일 이름은 남기지 않는다.
            log.info(
                    "첨부 확정 userId={} postId={} attachmentId={} contentType={} sizeBytes={}",
                    user.id(),
                    post.getId(),
                    attachment.getId(),
                    attachment.getContentType(),
                    attachment.getSizeBytes());
        }
    }

    private ArchivePostDetailResponse detail(Post post) {
        String authorName = support.authorNames(List.of(post)).get(post.getAuthorId());
        return ArchivePostDetailResponse.of(
                post,
                authorName,
                attachmentRepository.findByPostIdOrderByDisplayOrderAsc(post.getId()));
    }

    private Map<Long, Long> attachmentCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.countByPostIds(postIds).stream()
                .collect(
                        Collectors.toMap(
                                PostAttachmentCount::getPostId,
                                PostAttachmentCount::getAttachmentCount));
    }

    /** 같은 값이 두 번 나오면 400 (5-1-5). */
    private static void rejectDuplicates(Collection<?> values) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 커밋한 뒤 S3 객체를 지운다. 커밋 전에 지우면 롤백됐을 때 DB 행은 남고 파일만 사라진다. 실패해도 응답은 성공이고 남은 객체를 지울 수 있게 객체 키를 경고로
     * 남긴다 (3-2-6, DECISIONS.md B8).
     */
    private void deleteObjectsAfterCommit(Long postId, List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String objectKey : objectKeys) {
                            try {
                                objectStore.deleteAll(List.of(objectKey));
                            } catch (RuntimeException e) {
                                log.warn(
                                        "첨부 S3 객체 삭제 실패 objectKey={} postId={}",
                                        objectKey,
                                        postId,
                                        e);
                            }
                        }
                    }
                });
    }
}

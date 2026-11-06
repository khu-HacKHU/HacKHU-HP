package com.hackhu.hp.global.storage;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRecordRepository extends JpaRepository<UploadRecord, UUID> {}

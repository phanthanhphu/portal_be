package org.bsl.portal.repository;

import org.bsl.portal.model.Notice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NoticeRepository extends MongoRepository<Notice, String> {


}
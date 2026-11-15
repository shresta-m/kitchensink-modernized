package com.kitchensink.repository;



import com.kitchensink.model.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

/** CRUD from Spring Data; search, streaming and bulk insert come from {@link MemberRepositoryCustom}. */
public interface MemberRepository extends MongoRepository<Member, String>, MemberRepositoryCustom {}

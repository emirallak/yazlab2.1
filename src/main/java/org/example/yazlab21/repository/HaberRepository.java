package org.example.yazlab21.repository;

import org.example.yazlab21.model.Haber;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaberRepository extends MongoRepository<Haber, String> {


    boolean existsByLink(String link);


}
package org.example.yazlab21.repository;

import org.example.yazlab21.model.Haber;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HaberRepository extends MongoRepository<Haber, String> {

    boolean existsByLink(String link);

    List<Haber> findByHaberTuru(String haberTuru);

}
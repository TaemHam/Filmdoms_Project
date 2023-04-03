package com.filmdoms.community.article.repository;

import com.filmdoms.community.article.data.constant.Category;
import com.filmdoms.community.article.data.entity.Article;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByCategory(Category category, Pageable pageable);

    //JPA 기본 제공 findAll 메서드는 페이지 객체를 반환하므로 필요 없는 count 쿼리가 나감 -> 리스트를 반환하는 메서드를 따로 만듦
    @Query("SELECT a FROM Article a")
    List<Article> findAllReturnList(Pageable pageable);

    @Query("SELECT a FROM Article a " +
            "LEFT JOIN FETCH a.author.profileImage " +
            "LEFT JOIN FETCH a.content " +
            "WHERE a.id = :articleId")
    Optional<Article> findByIdWithAuthorProfileImageContent(@Param("articleId") Long articleId);
}

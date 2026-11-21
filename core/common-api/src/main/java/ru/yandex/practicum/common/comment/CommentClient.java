package ru.yandex.practicum.common.comment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "comment-service", path = "/internal/comments")
public interface CommentClient {

    @DeleteMapping("/by-author/{authorId}")
    void deleteByAuthor(@PathVariable("authorId") Long authorId);
}

// 文件: src/main/java/com/cupk/aiacademy/repository/paper/PaperQuestionRepository.java
package com.cupk.aiacademy.repository.paper;

import com.cupk.aiacademy.pojo.paper.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperQuestionRepository extends JpaRepository<PaperQuestion, Long> {
    List<PaperQuestion> findByPaperIdOrderBySortOrderAsc(Long paperId);
}

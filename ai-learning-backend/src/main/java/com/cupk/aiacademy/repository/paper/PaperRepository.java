// 文件: src/main/java/com/cupk/aiacademy/repository/paper/PaperRepository.java
package com.cupk.aiacademy.repository.paper;

import com.cupk.aiacademy.pojo.paper.Paper;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperRepository extends JpaRepository<Paper, Long> {
}

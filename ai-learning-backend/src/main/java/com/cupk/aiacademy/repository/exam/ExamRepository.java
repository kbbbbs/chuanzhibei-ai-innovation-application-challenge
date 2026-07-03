// 文件: src/main/java/com/cupk/aiacademy/repository/exam/ExamRepository.java
package com.cupk.aiacademy.repository.exam;

import com.cupk.aiacademy.pojo.exam.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
}

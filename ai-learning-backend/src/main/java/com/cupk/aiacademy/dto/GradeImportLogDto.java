package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.GradeImportLog;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GradeImportLogDto {
    @NotNull
    @Positive
    private Integer teacherId;

    @NotNull
    @Positive
    private Integer classId;

    @Size(max = 255)
    private String fileName;

    @Size(max = 255)
    private String fileUrl;

    @Min(0)
    private Integer totalCount;

    @Min(0)
    private Integer successCount;

    @Min(0)
    private Integer failCount;

    @Size(max = 2000)
    private String errorMessage;

    public GradeImportLog toEntity() {
        GradeImportLog log = new GradeImportLog();
        log.setTeacherId(teacherId);
        log.setClassId(classId);
        log.setFileName(fileName);
        log.setFileUrl(fileUrl);
        log.setTotalCount(totalCount);
        log.setSuccessCount(successCount);
        log.setFailCount(failCount);
        log.setErrorMessage(errorMessage);
        return log;
    }
}

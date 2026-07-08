package com.cupk.aiacademy.service;

import com.cupk.aiacademy.dto.PaperMarkResponse;
import com.cupk.aiacademy.dto.PaperMarkUpdateRequest;

import java.util.List;
import java.util.Map;

public interface PaperMarkService {
    List<PaperMarkResponse> listMarks(String keyword, Integer page, Integer size);
    PaperMarkResponse getMark(String paperId);
    Map<String, PaperMarkResponse> getMarks(List<String> paperIds);
    PaperMarkResponse saveMark(String paperId, PaperMarkUpdateRequest request);
    void deleteMark(String paperId);
}

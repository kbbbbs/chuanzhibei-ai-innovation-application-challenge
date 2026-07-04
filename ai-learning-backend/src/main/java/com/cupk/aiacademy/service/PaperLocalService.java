package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;

public interface PaperLocalService {
    Result search(String keyword, Integer page, Integer size);
    Result getDetail(String paperId);
    Result importFromArxiv(String keyword, Integer maxResults);
}

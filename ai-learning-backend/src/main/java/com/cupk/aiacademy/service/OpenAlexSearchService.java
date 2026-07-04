package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;

public interface OpenAlexSearchService {
    Result search(String keyword, Integer page, Integer size);
}

package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;

public interface ArxivSearchService {
    Result search(String keyword, Integer page, Integer size, String sortBy);
}

package com.cupk.aiacademy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.PaperMarkResponse;
import com.cupk.aiacademy.dto.PaperMarkUpdateRequest;
import com.cupk.aiacademy.entity.PaperUserMark;
import com.cupk.aiacademy.mapper.PaperUserMarkMapper;
import com.cupk.aiacademy.service.PaperMarkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperMarkServiceImpl implements PaperMarkService {
    private static final String READ = "READ";
    private static final String UNREAD = "UNREAD";

    private final PaperUserMarkMapper paperUserMarkMapper;

    public PaperMarkServiceImpl(PaperUserMarkMapper paperUserMarkMapper) {
        this.paperUserMarkMapper = paperUserMarkMapper;
    }

    @Override
    public List<PaperMarkResponse> listMarks(String keyword, Integer page, Integer size) {
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        QueryWrapper<PaperUserMark> query = new QueryWrapper<PaperUserMark>()
            .eq("user_id", userId)
            .and(keyword != null && !keyword.isBlank(), q -> q.like("title", keyword).or().like("paper_id", keyword))
            .orderByDesc("updated_at");
        Page<PaperUserMark> mpPage = new Page<>(safePage, safeSize);
        paperUserMarkMapper.selectPage(mpPage, query);
        return mpPage.getRecords().stream().map(mark -> toResponse(mark, mark.getPaperId())).toList();
    }

    @Override
    public PaperMarkResponse getMark(String paperId) {
        return toResponse(findCurrentUserMark(paperId), paperId);
    }

    @Override
    public Map<String, PaperMarkResponse> getMarks(List<String> paperIds) {
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null || paperIds == null || paperIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ids = paperIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PaperUserMark> marks = paperUserMarkMapper.selectList(new QueryWrapper<PaperUserMark>()
            .eq("user_id", userId)
            .in("paper_id", ids));
        Map<String, PaperUserMark> markMap = new LinkedHashMap<>();
        for (PaperUserMark mark : marks) {
            markMap.put(mark.getPaperId(), mark);
        }
        Map<String, PaperMarkResponse> response = new LinkedHashMap<>();
        for (String id : ids) {
            response.put(id, toResponse(markMap.get(id), id));
        }
        return response;
    }

    @Override
    @Transactional
    public PaperMarkResponse saveMark(String paperId, PaperMarkUpdateRequest request) {
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        PaperUserMark mark = findCurrentUserMark(paperId);
        if (mark == null) {
            mark = new PaperUserMark();
            mark.setUserId(userId);
            mark.setPaperId(paperId);
            mark.setCreatedAt(LocalDateTime.now());
        }
        if (request != null) {
            if (request.getFavorite() != null) {
                mark.setFavorite(request.getFavorite());
            } else if (mark.getFavorite() == null) {
                mark.setFavorite(false);
            }
            if (request.getReadStatus() != null && !request.getReadStatus().isBlank()) {
                mark.setReadStatus(normalizeReadStatus(request.getReadStatus()));
            } else if (mark.getReadStatus() == null || mark.getReadStatus().isBlank()) {
                mark.setReadStatus(UNREAD);
            }
            if (request.getNoteMd() != null) {
                mark.setNoteMd(request.getNoteMd());
            }
            if (request.getTitle() != null) {
                mark.setTitle(request.getTitle());
            }
            if (request.getSource() != null) {
                mark.setSource(request.getSource());
            }
        }
        if (mark.getFavorite() == null) {
            mark.setFavorite(false);
        }
        if (mark.getReadStatus() == null || mark.getReadStatus().isBlank()) {
            mark.setReadStatus(UNREAD);
        }
        if (mark.getNoteMd() == null) {
            mark.setNoteMd("");
        }
        mark.setUpdatedAt(LocalDateTime.now());
        if (mark.getId() == null) {
            paperUserMarkMapper.insert(mark);
        } else {
            paperUserMarkMapper.updateById(mark);
        }
        return toResponse(mark, paperId);
    }

    @Override
    @Transactional
    public void deleteMark(String paperId) {
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return;
        }
        paperUserMarkMapper.delete(new QueryWrapper<PaperUserMark>()
            .eq("user_id", userId)
            .eq("paper_id", paperId));
    }

    private PaperUserMark findCurrentUserMark(String paperId) {
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null || paperId == null || paperId.isBlank()) {
            return null;
        }
        return paperUserMarkMapper.selectOne(new QueryWrapper<PaperUserMark>()
            .eq("user_id", userId)
            .eq("paper_id", paperId)
            .last("LIMIT 1"));
    }

    private PaperMarkResponse toResponse(PaperUserMark mark, String paperId) {
        PaperMarkResponse response = new PaperMarkResponse();
        response.setPaperId(paperId);
        response.setFavorite(false);
        response.setReadStatus(UNREAD);
        response.setNoteMd("");
        if (mark == null) {
            return response;
        }
        response.setPaperId(mark.getPaperId());
        response.setTitle(mark.getTitle());
        response.setSource(mark.getSource());
        response.setFavorite(Boolean.TRUE.equals(mark.getFavorite()));
        response.setReadStatus(normalizeReadStatus(mark.getReadStatus()));
        response.setNoteMd(mark.getNoteMd() == null ? "" : mark.getNoteMd());
        response.setUpdatedAt(mark.getUpdatedAt());
        return response;
    }

    private String normalizeReadStatus(String value) {
        return READ.equalsIgnoreCase(value) ? READ : UNREAD;
    }
}

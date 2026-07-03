package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.ApiResponse;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Notice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/notices")
public class NoticeController {
    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeachingClassMapper classMapper;

    @GetMapping
    public ApiResponse<Page<Notice>> pages(@RequestParam(required = false) Integer publisherId,
                        @RequestParam(required = false) Integer classId,
                        @RequestParam(defaultValue = "") String noticeType,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Notice> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        if (publisherId != null && publisherId > 0) {
            queryWrapper.eq("publisher_id", publisherId);
        }
        if (classId != null && classId > 0) {
            queryWrapper.eq("class_id", classId);
        }
        if (noticeType != null && !noticeType.trim().isEmpty()) {
            queryWrapper.eq("notice_type", noticeType.trim());
        }
        queryWrapper.orderByDesc("id");
        noticeMapper.selectPage(page, queryWrapper);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<Notice> selectById(@PathVariable Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        return ApiResponse.success(notice);
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody Notice notice) {
        checkNotice(notice);
        notice.setTitle(notice.getTitle().trim());
        notice.setCreateTime(LocalDateTime.now());
        int row = noticeMapper.insert(notice);
        if (row <= 0) {
            throw new BusinessException("添加失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Integer id, @RequestBody Notice notice) {
        if (noticeMapper.selectById(id) == null) {
            throw new BusinessException("通知不存在");
        }
        checkNotice(notice);
        notice.setId(id);
        notice.setTitle(notice.getTitle().trim());
        int row = noticeMapper.updateById(notice);
        if (row <= 0) {
            throw new BusinessException("更新失败");
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        if (noticeMapper.selectById(id) == null) {
            throw new BusinessException("通知不存在");
        }
        int row = noticeMapper.deleteById(id);
        if (row <= 0) {
            throw new BusinessException("删除失败");
        }
        return ApiResponse.success(null);
    }

    private void checkNotice(Notice notice) {
        if (notice == null) {
            throw new BusinessException("通知信息不能为空");
        }
        if (notice.getPublisherId() == null || userMapper.selectById(notice.getPublisherId()) == null) {
            throw new BusinessException("发布用户不存在");
        }
        if (notice.getClassId() != null && classMapper.selectById(notice.getClassId()) == null) {
            throw new BusinessException("教学班不存在");
        }
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            throw new BusinessException("通知标题不能为空");
        }
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}

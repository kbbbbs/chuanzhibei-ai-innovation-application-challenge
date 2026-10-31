package com.cupk.aiacademy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.NoticeDto;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.validation.Valid;
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

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthUtils authUtils;

    @GetMapping
    public Result pages(@RequestParam(required = false) Integer publisherId,
                        @RequestParam(required = false) Integer classId,
                        @RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "") String noticeType,
                        @RequestParam(defaultValue = "1") Integer pageNum,
                        @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Notice> page = new Page<>(fixPageNum(pageNum), fixPageSize(pageSize));
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isTeacher(user)) {
            queryWrapper.eq("publisher_id", user.getUserId());
            if (classId != null && classId > 0) {
                permissionService.requireClassTeacher(classId);
                queryWrapper.eq("class_id", classId);
            }
        } else if (authUtils.isStudent(user)) {
            java.util.List<Integer> classIds = permissionService.studentClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                queryWrapper.isNull("class_id");
            } else {
                queryWrapper.and(wrapper -> wrapper.in("class_id", classIds).or().isNull("class_id"));
            }
        } else {
            if (publisherId != null && publisherId > 0) {
                queryWrapper.eq("publisher_id", publisherId);
            }
            if (classId != null && classId > 0) {
                queryWrapper.eq("class_id", classId);
            }
        }
        if (noticeType != null && !noticeType.trim().isEmpty()) {
            queryWrapper.eq("notice_type", noticeType.trim());
        }
        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like("title", title.trim());
        }
        queryWrapper.orderByDesc("id");
        noticeMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            return Result.error("404", "通知不存在");
        }
        checkNoticeReadPermission(notice);
        return Result.success(notice);
    }

    @PostMapping
    public Result add(@Valid @RequestBody NoticeDto request) {
        permissionService.requireTeacherOrAdmin();
        Notice notice = request.toEntity();
        notice.setPublisherId(permissionService.currentUser().getUserId());
        Result checkResult = checkNotice(notice);
        if (checkResult != null) {
            return checkResult;
        }
        notice.setTitle(notice.getTitle().trim());
        notice.setCreateTime(LocalDateTime.now());
        int row = noticeMapper.insert(notice);
        return row > 0 ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody NoticeDto request) {
        Notice notice = request.toEntity();
        Notice old = noticeMapper.selectById(id);
        if (old == null) {
            return Result.error("404", "通知不存在");
        }
        checkNoticeWritePermission(old);
        notice.setPublisherId(permissionService.currentUser().getUserId());
        Result checkResult = checkNotice(notice);
        if (checkResult != null) {
            return checkResult;
        }
        notice.setId(id);
        notice.setTitle(notice.getTitle().trim());
        int row = noticeMapper.updateById(notice);
        return row > 0 ? Result.success() : Result.error();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            return Result.error("404", "通知不存在");
        }
        checkNoticeWritePermission(notice);
        int row = noticeMapper.deleteById(id);
        return row > 0 ? Result.success() : Result.error();
    }

    private Result checkNotice(Notice notice) {
        if (notice == null) {
            return Result.error("400", "通知信息不能为空");
        }
        if (notice.getPublisherId() == null || userMapper.selectById(notice.getPublisherId()) == null) {
            return Result.error("404", "发布用户不存在");
        }
        if (notice.getClassId() != null && classMapper.selectById(notice.getClassId()) == null) {
            return Result.error("404", "教学班不存在");
        }
        if (notice.getClassId() != null && authUtils.isTeacher(permissionService.currentUser())) {
            permissionService.requireClassTeacher(notice.getClassId());
        }
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            return Result.error("400", "通知标题不能为空");
        }
        return null;
    }

    private void checkNoticeReadPermission(Notice notice) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (authUtils.isAdmin(user)) {
            return;
        }
        if (notice.getClassId() == null) {
            return;
        }
        if (authUtils.isTeacher(user)) {
            permissionService.requireClassTeacher(notice.getClassId());
            return;
        }
        if (!permissionService.isStudentInClass(notice.getClassId(), user.getUserId())) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该通知");
        }
    }

    private void checkNoticeWritePermission(Notice notice) {
        permissionService.requireTeacherOrAdmin();
        if (authUtils.isAdmin(permissionService.currentUser())) {
            return;
        }
        if (notice.getClassId() == null) {
            if (!permissionService.currentUser().getUserId().equals(notice.getPublisherId())) {
                throw new com.cupk.aiacademy.common.ForbiddenException("无权维护该通知");
            }
            return;
        }
        permissionService.requireClassTeacher(notice.getClassId());
    }

    private int fixPageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    private int fixPageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }
}

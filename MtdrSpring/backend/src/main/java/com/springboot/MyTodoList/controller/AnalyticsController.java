package com.springboot.MyTodoList.controller;

import com.springboot.MyTodoList.dto.*;
import com.springboot.MyTodoList.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired
    private AnalyticsService analyticsService;

    /**
     * GET /api/analytics/velocity?weeks=8
     * Returns tasks created vs completed per ISO week for the last N weeks.
     */
    @GetMapping("/velocity")
    public ResponseEntity<List<VelocityDTO>> getVelocity(
            Authentication auth,
            @RequestParam(defaultValue = "8") int weeks,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/velocity - User: {} - weeks: {}, projectId: {}, sprintId: {}", userId, weeks, projectId, sprintId);
        try {
            List<VelocityDTO> result = analyticsService.getVelocity(userId, weeks, projectId, sprintId);
            log.info("✅ [SUCCESS] GET /analytics/velocity - {} data points returned for user {}", result.size(), userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/velocity - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/priority
     * Returns task count grouped by priority level.
     */
    @GetMapping("/priority")
    public ResponseEntity<List<PriorityDistributionDTO>> getPriorityDistribution(
            Authentication auth,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/priority - User: {}, projectId: {}, sprintId: {}", userId, projectId, sprintId);
        try {
            List<PriorityDistributionDTO> result = analyticsService.getPriorityDistribution(userId, projectId, sprintId);
            log.info("✅ [SUCCESS] GET /analytics/priority - {} priority buckets for user {}", result.size(), userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/priority - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/worked-hours?weeks=8
     * Returns sum of worked hours per user per ISO week.
     */
    @GetMapping("/worked-hours")
    public ResponseEntity<List<WorkedHoursDTO>> getWorkedHours(
            Authentication auth,
            @RequestParam(defaultValue = "8") int weeks,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/worked-hours - User: {} - weeks: {}, projectId: {}, sprintId: {}", userId, weeks, projectId, sprintId);
        try {
            List<WorkedHoursDTO> result = analyticsService.getWorkedHoursPerUser(userId, weeks, projectId, sprintId);
            log.info("✅ [SUCCESS] GET /analytics/worked-hours - {} entries for user {}", result.size(), userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/worked-hours - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/kpis?projectId=1&sprintId=2
     * Returns KPI summary for a project, optionally scoped to a single sprint:
     *   totalTasks, completedTasks, totalHoursWorked, totalHoursEstimated, completionRate
     * sprintId is optional — omit it to aggregate across all sprints in the project.
     */
    @GetMapping("/kpis")
    public ResponseEntity<SprintKpiDTO> getSprintKpis(
            Authentication auth,
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/kpis - User: {} - projectId: {} - sprintId: {}", userId, projectId, sprintId);
        try {
            SprintKpiDTO result = analyticsService.getSprintKpis(userId, projectId, sprintId);
            log.info("✅ [SUCCESS] GET /analytics/kpis - project: '{}', sprint: '{}', completion: {}%",
                    result.getProjectName(), result.getSprintName(), result.getCompletionRate());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/kpis - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/velocity-by-sprint?projectId=1
     * Returns tasks created vs completed grouped by sprint (not by week).
     */
    @GetMapping("/velocity-by-sprint")
    public ResponseEntity<List<VelocityDTO>> getVelocityBySprint(
            Authentication auth,
            @RequestParam Long projectId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/velocity-by-sprint - User: {}, projectId: {}", userId, projectId);
        try {
            List<VelocityDTO> result = analyticsService.getVelocityBySprint(userId, projectId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/velocity-by-sprint - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/worked-hours-by-sprint?projectId=1
     * Returns worked hours per user grouped by sprint (not by week).
     */
    @GetMapping("/worked-hours-by-sprint")
    public ResponseEntity<List<WorkedHoursDTO>> getWorkedHoursBySprint(
            Authentication auth,
            @RequestParam Long projectId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/worked-hours-by-sprint - User: {}, projectId: {}", userId, projectId);
        try {
            List<WorkedHoursDTO> result = analyticsService.getWorkedHoursBySprint(userId, projectId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/worked-hours-by-sprint - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/analytics/task-distribution
     * Returns task count per team member as percentages.
     */
    @GetMapping("/task-distribution")
    public ResponseEntity<List<TaskDistributionDTO>> getTaskDistribution(
            Authentication auth,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = (Long) auth.getPrincipal();
        log.info("🚀 [REQUEST] GET /analytics/task-distribution - User: {}, projectId: {}, sprintId: {}", userId, projectId, sprintId);
        try {
            List<TaskDistributionDTO> result = analyticsService.getTaskDistribution(userId, projectId, sprintId);
            log.info("✅ [SUCCESS] GET /analytics/task-distribution - {} members for user {}", result.size(), userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("❌ [ERROR] GET /analytics/task-distribution - Failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

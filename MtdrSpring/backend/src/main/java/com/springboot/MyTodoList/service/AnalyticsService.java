package com.springboot.MyTodoList.service;

import com.springboot.MyTodoList.dto.*;
import com.springboot.MyTodoList.model.AppUser;
import com.springboot.MyTodoList.model.Project;
import com.springboot.MyTodoList.model.Sprint;
import com.springboot.MyTodoList.model.Task;
import com.springboot.MyTodoList.model.Team;
import com.springboot.MyTodoList.model.TeamMembership;
import com.springboot.MyTodoList.repository.AppUserRepository;
import com.springboot.MyTodoList.repository.ProjectRepository;
import com.springboot.MyTodoList.repository.SprintRepository;
import com.springboot.MyTodoList.repository.TaskRepository;
import com.springboot.MyTodoList.repository.TeamMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    @Autowired private TaskRepository taskRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private TeamMembershipRepository membershipRepository;

    // ─────────────────────────────────────────────────────────────
    // SHARED HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Returns tasks filtered by project+sprint when provided, otherwise all team tasks. */
    private List<Task> getFilteredTasks(Long userId, Long projectId, Long sprintId) {
        if (projectId != null && sprintId != null) {
            return taskRepository.findByProjectIdAndSprintId(projectId, sprintId);
        }
        if (projectId != null) {
            return taskRepository.findByProjectId(projectId);
        }
        return getTasksForUser(userId);
    }

    private List<Task> getTasksForUser(Long userId) {
        log.debug("🔍 [LOOKUP] Fetching tasks for user {}", userId);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<TeamMembership> memberships = membershipRepository.findByUser(user);
        List<Team> teams = memberships.stream().map(TeamMembership::getTeam).collect(Collectors.toList());

        if (teams.isEmpty()) {
            log.info("⚠️ [WARN] User {} has no teams — returning empty task list for analytics", userId);
            return List.of();
        }

        List<Project> projects = projectRepository.findByTeamIn(teams);
        if (projects.isEmpty()) {
            log.info("⚠️ [WARN] User {} has no projects — returning empty task list for analytics", userId);
            return List.of();
        }

        List<Task> tasks = taskRepository.findByProjectIn(projects);
        log.debug("✅ [LOOKUP] Found {} tasks for analytics aggregation", tasks.size());
        return tasks;
    }

    /** Format LocalDateTime → ISO week label, e.g. "2026-W14" */
    private String toWeekLabel(LocalDateTime dt) {
        if (dt == null) return null;
        int year = dt.get(IsoFields.WEEK_BASED_YEAR);
        int week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    /** Sorted list of the last N ISO week labels, oldest first */
    private List<String> lastNWeekLabels(int n) {
        List<String> labels = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate w = today.minusWeeks(i);
            int year = w.get(IsoFields.WEEK_BASED_YEAR);
            int week = w.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            labels.add(String.format("%d-W%02d", year, week));
        }
        return labels;
    }

    // ─────────────────────────────────────────────────────────────
    // 1. VELOCITY: tasks created vs completed per week
    // ─────────────────────────────────────────────────────────────

    public List<VelocityDTO> getVelocity(Long userId, int weeks, Long projectId, Long sprintId) {
        log.info("🚀 [ANALYTICS] Computing velocity for user {} — last {} weeks, project={}, sprint={}", userId, weeks, projectId, sprintId);

        List<Task> tasks = getFilteredTasks(userId, projectId, sprintId);
        List<String> weekLabels = lastNWeekLabels(weeks);

        // Count created per week
        Map<String, Integer> created = new HashMap<>();
        for (Task t : tasks) {
            String label = toWeekLabel(t.getCreatedAt());
            if (label != null && weekLabels.contains(label)) {
                created.merge(label, 1, Integer::sum);
            }
        }

        // Count completed per week
        Map<String, Integer> completed = new HashMap<>();
        for (Task t : tasks) {
            if ("done".equals(t.getStatus())) {
                String label = toWeekLabel(t.getCompletedAt());
                if (label != null && weekLabels.contains(label)) {
                    completed.merge(label, 1, Integer::sum);
                }
            }
        }

        List<VelocityDTO> result = weekLabels.stream()
                .map(w -> new VelocityDTO(w, created.getOrDefault(w, 0), completed.getOrDefault(w, 0)))
                .collect(Collectors.toList());

        log.info("✅ [ANALYTICS] Velocity computed: {} data points for user {}", result.size(), userId);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 2. PRIORITY DISTRIBUTION
    // ─────────────────────────────────────────────────────────────

    public List<PriorityDistributionDTO> getPriorityDistribution(Long userId, Long projectId, Long sprintId) {
        log.info("🔥 [ANALYTICS] Computing priority distribution for user {}, project={}, sprint={}", userId, projectId, sprintId);

        List<Task> tasks = getFilteredTasks(userId, projectId, sprintId);

        Map<String, Long> counts = tasks.stream()
                .filter(t -> t.getPriority() != null)
                .collect(Collectors.groupingBy(Task::getPriority, Collectors.counting()));

        // Ensure all priority levels appear (even with 0)
        List<String> allPriorities = List.of("low", "medium", "high", "critical");
        List<PriorityDistributionDTO> result = allPriorities.stream()
                .map(p -> new PriorityDistributionDTO(p, counts.getOrDefault(p, 0L).intValue()))
                .filter(d -> d.getCount() > 0)  // skip empty buckets
                .collect(Collectors.toList());

        log.info("✅ [ANALYTICS] Priority distribution computed: {} priorities for user {}", result.size(), userId);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 3. WORKED HOURS PER USER PER WEEK
    // ─────────────────────────────────────────────────────────────

    public List<WorkedHoursDTO> getWorkedHoursPerUser(Long userId, int weeks, Long projectId, Long sprintId) {
        log.info("⏱️ [ANALYTICS] Computing worked hours per user for user {} — last {} weeks, project={}, sprint={}", userId, weeks, projectId, sprintId);

        List<Task> tasks = getFilteredTasks(userId, projectId, sprintId);
        List<String> weekLabels = lastNWeekLabels(weeks);

        // key: "week|userName" → sum of workedHours
        Map<String, Integer> hourMap = new LinkedHashMap<>();

        for (Task t : tasks) {
            if (t.getWorkedHours() == null || t.getWorkedHours() == 0) continue;
            if (t.getAssignee() == null) continue;

            String label = toWeekLabel(t.getUpdatedAt());
            if (label == null || !weekLabels.contains(label)) continue;

            String key = label + "|" + t.getAssignee().getName();
            hourMap.merge(key, t.getWorkedHours(), Integer::sum);
        }

        List<WorkedHoursDTO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : hourMap.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            result.add(new WorkedHoursDTO(parts[0], parts[1], entry.getValue()));
        }

        // Sort by week asc, then name
        result.sort(Comparator.comparing(WorkedHoursDTO::getWeek).thenComparing(WorkedHoursDTO::getUserName));

        log.info("✅ [ANALYTICS] Worked hours computed: {} entries for user {}", result.size(), userId);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 4. SPRINT KPIs: hours worked, tasks completed, completion rate
    //    Filters: projectId (required), sprintId (optional)
    // ─────────────────────────────────────────────────────────────

    public SprintKpiDTO getSprintKpis(Long userId, Long projectId, Long sprintId) {
        log.info("📊 [ANALYTICS] Computing sprint KPIs - user: {}, project: {}, sprint: {}", userId, projectId, sprintId);

        // Validate the project exists and the user has access (belongs to a team that owns it)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        List<Task> tasks;
        String sprintName = "All Sprints";

        if (sprintId != null) {
            Sprint sprint = sprintRepository.findById(sprintId)
                    .orElseThrow(() -> new RuntimeException("Sprint not found: " + sprintId));
            sprintName = sprint.getName();
            tasks = taskRepository.findByProjectIdAndSprintId(projectId, sprintId);
        } else {
            tasks = taskRepository.findByProjectId(projectId);
        }

        int totalTasks      = tasks.size();
        int completedTasks  = (int) tasks.stream().filter(t -> "done".equals(t.getStatus())).count();
        int hoursWorked     = tasks.stream().mapToInt(t -> t.getWorkedHours()  != null ? t.getWorkedHours()  : 0).sum();
        int hoursEstimated  = tasks.stream().mapToInt(t -> t.getStoryPoints()  != null ? t.getStoryPoints()  : 0).sum();
        double completionRate = totalTasks == 0 ? 0.0
                : Math.round((completedTasks * 100.0 / totalTasks) * 10.0) / 10.0;

        log.info("✅ [ANALYTICS] Sprint KPIs — project: '{}', sprint: '{}', tasks: {}/{}, hours: {}h worked / {}h estimated",
                project.getName(), sprintName, completedTasks, totalTasks, hoursWorked, hoursEstimated);

        return new SprintKpiDTO(
                sprintId != null ? sprintId.toString() : null,
                sprintName,
                projectId.toString(),
                project.getName(),
                totalTasks,
                completedTasks,
                hoursWorked,
                hoursEstimated,
                completionRate
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 5a. VELOCITY BY SPRINT (tasks created vs completed per sprint)
    // ─────────────────────────────────────────────────────────────

    public List<VelocityDTO> getVelocityBySprint(Long userId, Long projectId) {
        log.info("🚀 [ANALYTICS] Computing velocity by sprint for user {}, project={}", userId, projectId);

        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        sprints.sort(Comparator.comparing(Sprint::getName));

        List<VelocityDTO> result = new ArrayList<>();
        for (Sprint sprint : sprints) {
            List<Task> tasks = taskRepository.findByProjectIdAndSprintId(projectId, sprint.getId());
            int created = tasks.size();
            int completed = (int) tasks.stream().filter(t -> "done".equals(t.getStatus())).count();
            result.add(new VelocityDTO(sprint.getName(), created, completed));
        }

        log.info("✅ [ANALYTICS] Velocity by sprint: {} sprints for user {}", result.size(), userId);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 5b. WORKED HOURS BY SPRINT (hours per user per sprint)
    // ─────────────────────────────────────────────────────────────

    public List<WorkedHoursDTO> getWorkedHoursBySprint(Long userId, Long projectId) {
        log.info("⏱️ [ANALYTICS] Computing worked hours by sprint for user {}, project={}", userId, projectId);

        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        sprints.sort(Comparator.comparing(Sprint::getName));

        List<WorkedHoursDTO> result = new ArrayList<>();
        for (Sprint sprint : sprints) {
            List<Task> tasks = taskRepository.findByProjectIdAndSprintId(projectId, sprint.getId());

            Map<String, Integer> hoursByUser = new LinkedHashMap<>();
            Map<String, String> userNames = new LinkedHashMap<>();

            for (Task t : tasks) {
                if (t.getWorkedHours() == null || t.getWorkedHours() == 0) continue;
                if (t.getAssignee() == null) continue;
                String uid = t.getAssignee().getId().toString();
                hoursByUser.merge(uid, t.getWorkedHours(), Integer::sum);
                userNames.put(uid, t.getAssignee().getName());
            }

            for (Map.Entry<String, Integer> entry : hoursByUser.entrySet()) {
                result.add(new WorkedHoursDTO(sprint.getName(), userNames.get(entry.getKey()), entry.getValue()));
            }
        }

        log.info("✅ [ANALYTICS] Worked hours by sprint: {} entries for user {}", result.size(), userId);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 6. TASK DISTRIBUTION BY TEAM MEMBER (percentages)
    // ─────────────────────────────────────────────────────────────

    public List<TaskDistributionDTO> getTaskDistribution(Long userId, Long projectId, Long sprintId) {
        log.info("👥 [ANALYTICS] Computing task distribution for user {}, project={}, sprint={}", userId, projectId, sprintId);

        List<Task> tasks = getFilteredTasks(userId, projectId, sprintId);

        // Group by assignee
        Map<String, long[]> byAssignee = new LinkedHashMap<>(); // id → [count]
        Map<String, String> nameById = new HashMap<>();

        for (Task t : tasks) {
            if (t.getAssignee() == null) {
                // Track unassigned
                byAssignee.computeIfAbsent("unassigned", k -> new long[]{0})[0]++;
                nameById.put("unassigned", "Unassigned");
            } else {
                String uid = t.getAssignee().getId().toString();
                byAssignee.computeIfAbsent(uid, k -> new long[]{0})[0]++;
                nameById.put(uid, t.getAssignee().getName());
            }
        }

        long total = tasks.size();
        if (total == 0) {
            log.info("⚠️ [ANALYTICS] No tasks found for distribution — returning empty list");
            return List.of();
        }

        List<TaskDistributionDTO> result = byAssignee.entrySet().stream()
                .map(e -> {
                    int count = (int) e.getValue()[0];
                    double pct = Math.round((count * 100.0 / total) * 10.0) / 10.0;
                    return new TaskDistributionDTO(e.getKey(), nameById.get(e.getKey()), count, pct);
                })
                .sorted(Comparator.comparingInt(TaskDistributionDTO::getTaskCount).reversed())
                .collect(Collectors.toList());

        log.info("✅ [ANALYTICS] Task distribution computed: {} assignees for user {}", result.size(), userId);
        return result;
    }
}

package com.springboot.MyTodoList.service;

import com.springboot.MyTodoList.dto.CreateTaskRequest;
import com.springboot.MyTodoList.dto.TaskDTO;
import com.springboot.MyTodoList.dto.UpdateTaskRequest;
import com.springboot.MyTodoList.model.AppUser;
import com.springboot.MyTodoList.model.Project;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    @Autowired private TaskRepository taskRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private TeamMembershipRepository membershipRepository;

    // ─────────────────────────────────────────────────────────────
    // GET TASKS (with optional filters; defaults to user's projects)
    // ─────────────────────────────────────────────────────────────

    public List<TaskDTO> getTasks(Long userId, String projectId, String sprintId, String assigneeId, String status) {
        log.info("📋 [TASK] Fetching tasks for user {} — filters: projectId={}, sprintId={}, assigneeId={}, status={}",
                userId, projectId, sprintId, assigneeId, status);

        List<Task> tasks;

        if (projectId != null && !projectId.isBlank()) {
            log.debug("🔍 [LOOKUP] Filtering tasks by projectId: {}", projectId);
            tasks = taskRepository.findByProjectId(Long.parseLong(projectId));
        } else if (sprintId != null && !sprintId.isBlank()) {
            log.debug("🔍 [LOOKUP] Filtering tasks by sprintId: {}", sprintId);
            tasks = taskRepository.findBySprintId(Long.parseLong(sprintId));
        } else if (assigneeId != null && !assigneeId.isBlank()) {
            log.debug("🔍 [LOOKUP] Filtering tasks by assigneeId: {}", assigneeId);
            tasks = taskRepository.findByAssigneeId(Long.parseLong(assigneeId));
        } else if (status != null && !status.isBlank()) {
            log.debug("🔍 [LOOKUP] Filtering tasks by status: {}", status);
            tasks = taskRepository.findByStatus(status);
        } else {
            // No filter: return all tasks belonging to the user's teams/projects
            log.debug("🔍 [LOOKUP] No filter — fetching all tasks for user {}", userId);
            AppUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            List<TeamMembership> memberships = membershipRepository.findByUser(user);
            List<Team> teams = memberships.stream().map(TeamMembership::getTeam).collect(Collectors.toList());
            if (teams.isEmpty()) {
                log.info("⚠️ [WARN] User {} has no teams — returning empty task list", userId);
                return List.of();
            }
            List<Project> projects = projectRepository.findByTeamIn(teams);
            if (projects.isEmpty()) {
                log.info("⚠️ [WARN] User {} has no projects — returning empty task list", userId);
                return List.of();
            }
            tasks = taskRepository.findByProjectIn(projects);
        }

        log.info("✅ [SUCCESS] Found {} task(s)", tasks.size());
        return tasks.stream().map(TaskDTO::from).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // GET TASK BY ID
    // ─────────────────────────────────────────────────────────────

    public TaskDTO getTaskById(Long taskId) {
        log.info("📋 [TASK] Fetching task by ID: {}", taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("❌ [ERROR] Task not found with ID: {}", taskId);
                    return new RuntimeException("Task not found");
                });

        log.info("✅ [SUCCESS] Returning task '{}' (ID: {})", task.getTitle(), taskId);
        return TaskDTO.from(task);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE TASK
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public TaskDTO createTask(Long userId, CreateTaskRequest request) {
        log.info("📋 [TASK] User {} creating task '{}' (type: '{}', priority: '{}', projectId: '{}')",
                userId, request.getTitle(), request.getType(), request.getPriority(), request.getProjectId());

        log.debug("🔍 [LOOKUP] Fetching creator by ID: {}", userId);
        AppUser creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Long projectId = Long.parseLong(request.getProjectId());
        log.debug("🔍 [LOOKUP] Fetching project by ID: {}", projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setType(request.getType());
        task.setProject(project);
        task.setStoryPoints(request.getStoryPoints());
        task.setCreatedBy(creator);
        task.setStatus("todo");

        if (request.getWorkedHours() != null) {
            task.setWorkedHours(request.getWorkedHours());
            log.debug("⏱️ [HOURS] Task worked hours set to: {}", request.getWorkedHours());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            task.setTags(request.getTags());
            log.debug("🏷️ [TAG] Task tags: {}", request.getTags());
        }

        if (request.getAssigneeId() != null && !request.getAssigneeId().isBlank()) {
            Long assigneeId = Long.parseLong(request.getAssigneeId());
            log.debug("🔍 [LOOKUP] Fetching assignee by ID: {}", assigneeId);
            userRepository.findById(assigneeId).ifPresentOrElse(
                    assignee -> {
                        task.setAssignee(assignee);
                        log.info("👤 [ASSIGN] Task '{}' assigned to user {} ({})",
                                request.getTitle(), assigneeId, assignee.getName());
                    },
                    () -> log.warn("⚠️ [WARN] Assignee ID {} not found — task will be unassigned", assigneeId)
            );
        }

        if (request.getSprintId() != null && !request.getSprintId().isBlank()) {
            Long sprintId = Long.parseLong(request.getSprintId());
            log.debug("🔍 [LOOKUP] Fetching sprint by ID: {}", sprintId);
            sprintRepository.findById(sprintId).ifPresent(sprint -> {
                task.setSprint(sprint);
                log.info("🏃 [SPRINT] Task '{}' added to sprint '{}'", request.getTitle(), sprint.getName());
            });
        }

        log.info("💾 [SAVE] Saving task '{}' in project {} ('{}')", request.getTitle(), projectId, project.getName());
        Task saved = taskRepository.save(task);
        log.info("✅ [SUCCESS] Task '{}' (ID: {}) created by user {}", saved.getTitle(), saved.getId(), userId);
        return TaskDTO.from(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE TASK FROM TELEGRAM
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public TaskDTO createTaskFromTelegram(String telegramUsername, String title, Integer expectedHours) {
        log.info("🤖 [TELEGRAM] Creating task from Telegram user '{}', title='{}', expectedHours={}",
                telegramUsername, title, expectedHours);

        if (telegramUsername == null || telegramUsername.isBlank()) {
            throw new RuntimeException("Telegram username not found");
        }

        String cleanUsername = telegramUsername.startsWith("@")
                ? telegramUsername.substring(1)
                : telegramUsername;

        AppUser user = userRepository.findByTelegramUsernameIgnoreCase(cleanUsername)
                .orElseThrow(() -> new RuntimeException(
                        "No app user is linked to Telegram username: " + cleanUsername));

        List<TeamMembership> memberships = membershipRepository.findByUser(user);

        if (memberships.isEmpty()) {
            throw new RuntimeException("This user does not belong to any team");
        }

        List<Team> teams = memberships.stream()
                .map(TeamMembership::getTeam)
                .collect(Collectors.toList());

        List<Project> projects = projectRepository.findByTeamIn(teams);

        if (projects.isEmpty()) {
            throw new RuntimeException("No projects found for this user's teams");
        }

        Project selectedProject = projects.stream()
                .filter(project -> "active".equalsIgnoreCase(project.getStatus()))
                .findFirst()
                .orElse(projects.get(0));

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(title);
        request.setDescription("Task created from Telegram bot");
        request.setPriority("medium");
        request.setType("task");
        request.setProjectId(String.valueOf(selectedProject.getId()));
        request.setWorkedHours(expectedHours);

        return createTask(user.getId(), request);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE TASK
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public TaskDTO updateTask(Long userId, Long taskId, UpdateTaskRequest request) {
        log.info("📋 [TASK] User {} updating task {}", userId, taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("❌ [ERROR] Task not found with ID: {}", taskId);
                    return new RuntimeException("Task not found");
                });

        if (request.getTitle() != null) {
            log.debug("✏️ [UPDATE] Task {} title → '{}'", taskId, request.getTitle());
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            log.info("🔄 [STATUS] Task {} status → '{}'", taskId, request.getStatus());
            task.setStatus(request.getStatus());
            // Auto-set completedAt when task transitions to "done"
            if ("done".equals(request.getStatus()) && task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
                log.info("✅ [COMPLETE] Task {} marked done — completedAt set to now", taskId);
            } else if (!"done".equals(request.getStatus())) {
                task.setCompletedAt(null); // clear if moved back from done
                log.debug("🔄 [COMPLETE] Task {} moved out of done — completedAt cleared", taskId);
            }
        }
        if (request.getPriority() != null) {
            log.debug("🔄 [UPDATE] Task {} priority → '{}'", taskId, request.getPriority());
            task.setPriority(request.getPriority());
        }
        if (request.getType() != null) {
            task.setType(request.getType());
        }
        if (request.getStoryPoints() != null) {
            task.setStoryPoints(request.getStoryPoints());
        }
        if (request.getTags() != null) {
            task.setTags(request.getTags());
        }
        if (request.getWorkedHours() != null) {
            log.debug("⏱️ [HOURS] Task {} workedHours → {}", taskId, request.getWorkedHours());
            task.setWorkedHours(request.getWorkedHours());
        }
        if (request.getAssigneeId() != null) {
            if (request.getAssigneeId().isBlank()) {
                task.setAssignee(null);
                log.info("👤 [ASSIGN] Task {} unassigned", taskId);
            } else {
                Long assigneeId = Long.parseLong(request.getAssigneeId());
                userRepository.findById(assigneeId).ifPresent(assignee -> {
                    task.setAssignee(assignee);
                    log.info("👤 [ASSIGN] Task {} assigned to user {} ({})", taskId, assigneeId, assignee.getName());
                });
            }
        }
        if (request.getSprintId() != null) {
            if (request.getSprintId().isBlank()) {
                task.setSprint(null);
                log.info("🏃 [SPRINT] Task {} removed from sprint", taskId);
            } else {
                Long sprintId = Long.parseLong(request.getSprintId());
                sprintRepository.findById(sprintId).ifPresent(sprint -> {
                    task.setSprint(sprint);
                    log.info("🏃 [SPRINT] Task {} moved to sprint '{}'", taskId, sprint.getName());
                });
            }
        }

        log.info("💾 [SAVE] Saving updated task {} ('{}')", taskId, task.getTitle());
        Task updated = taskRepository.save(task);
        log.info("✅ [SUCCESS] Task {} updated successfully", taskId);
        return TaskDTO.from(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE TASK
    // ─────────────────────────────────────────────────────────────


    // ─────────────────────────────────────────────────────────────
    // GET TASKS FOR BOT (Telegram)
    // Runs @Transactional so lazy fields resolve without
    // LazyInitializationException when called from BotActions.
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksForUser(Long userId) {
        log.info("\uD83E\uDD16 [BOT] Fetching tasks assigned to user {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<Task> tasks = taskRepository.findByAssigneeId(userId);

        log.info("\u2705 [BOT] Found {} task(s) for user {}", tasks.size(), userId);

        // Access all lazy fields inside this transaction so they are loaded
        return tasks.stream().map(task -> {
            // Touch lazy associations to force initialization
            if (task.getAssignee() != null) task.getAssignee().getId();
            if (task.getSprint() != null) task.getSprint().getId();
            if (task.getProject() != null) task.getProject().getId();
            if (task.getCreatedBy() != null) task.getCreatedBy().getId();
            if (task.getComments() != null) task.getComments().size();
            return TaskDTO.from(task);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        log.info("📋 [TASK] User {} deleting task {}", userId, taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("❌ [ERROR] Task {} not found — cannot delete", taskId);
                    return new RuntimeException("Task not found");
                });

        log.info("🗑️ [DELETE] Deleting task {} ('{}')", taskId, task.getTitle());
        taskRepository.delete(task);
        log.info("✅ [SUCCESS] Task {} deleted successfully", taskId);
    }
}


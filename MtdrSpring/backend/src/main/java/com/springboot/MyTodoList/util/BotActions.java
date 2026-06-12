package com.springboot.MyTodoList.util;

import com.springboot.MyTodoList.dto.TaskDTO;
import com.springboot.MyTodoList.dto.UpdateTaskRequest;
import com.springboot.MyTodoList.model.AppUser;
import com.springboot.MyTodoList.service.DeepSeekService;
import com.springboot.MyTodoList.service.JoinCodeService;
import com.springboot.MyTodoList.service.TaskService;
import com.springboot.MyTodoList.service.ToDoItemService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class BotActions {

    private static final Logger logger = LoggerFactory.getLogger(BotActions.class);

    private String requestText;
    private Long chatId;
    private String telegramUsername;
    private boolean exit;

    private final TelegramClient telegramClient;
    private final ToDoItemService todoService;
    private final DeepSeekService deepSeekService;
    private final TaskService taskService;
    private final JoinCodeService joinCodeService;

    private static final Map<Long, UserSession> sessions = new HashMap<>();

    public BotActions(
            TelegramClient telegramClient,
            ToDoItemService todoService,
            DeepSeekService deepSeekService,
            TaskService taskService,
            JoinCodeService joinCodeService
    ) {
        this.telegramClient = telegramClient;
        this.todoService = todoService;
        this.deepSeekService = deepSeekService;
        this.taskService = taskService;
        this.joinCodeService = joinCodeService;
        this.exit = false;
    }

    public void setRequestText(String requestText) {
        this.requestText = requestText;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public void setTelegramUsername(String telegramUsername) {
        this.telegramUsername = telegramUsername;
    }

    private UserSession getSession() {
        return sessions.computeIfAbsent(chatId, k -> new UserSession());
    }

    private void send(String message) {
        BotHelper.sendMessageToTelegram(chatId, message, telegramClient, null);
    }

    private void sendKb(String message, ReplyKeyboardMarkup keyboard) {
        BotHelper.sendMessageToTelegram(chatId, message, telegramClient, keyboard);
    }

    private ReplyKeyboardMarkup buildStatusKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add("todo");
        row.add("in-progress");
        row.add("done");

        return ReplyKeyboardMarkup.builder()
                .keyboard(Arrays.asList(row))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }

    public boolean handleState() {
        UserSession session = getSession();

        if (requestText.equalsIgnoreCase("/cancel")) {
            session.resetConversation();
            send("✅ Cancelled.");
            return true;
        }

        if (session.getState() == ConversationState.CONFIG_WAITING_CODE) {
            try {
                AppUser user = joinCodeService.linkTelegramAccount(
                        requestText.trim().toUpperCase(),
                        chatId,
                        telegramUsername
                );

                session.setAppUserId(user.getId());
                session.resetConversation();

                send(BotMessages.CONFIG_SUCC.getMessage());
            } catch (Exception e) {
                logger.error("Error linking Telegram account", e);
                session.resetConversation();
                send(BotMessages.CONFIG_FAIL.getMessage());
            }

            return true;
        }

        if (session.getState() == ConversationState.WAITING_ID) {
            session.setTaskId(requestText.trim());

            switch (session.getAction()) {
                case MOD_NAME:
                    session.setState(ConversationState.WAITING_NAME);
                    send(BotMessages.MOD_NAME.getMessage());
                    break;

                case MOD_STATUS:
                    session.setState(ConversationState.WAITING_STATUS);
                    sendKb(BotMessages.MOD_STATUS.getMessage(), buildStatusKeyboard());
                    break;

                case MOD_WORKED:
                    session.setState(ConversationState.WAITING_WORKED);
                    send(BotMessages.MOD_WORKED.getMessage());
                    break;

                case MOD_EXPECTED:
                    session.setState(ConversationState.WAITING_EXPECTED);
                    send(BotMessages.MOD_EXPECTED.getMessage());
                    break;

                default:
                    session.resetConversation();
                    return false;
            }

            return true;
        }

        if (handleModifyValue(session)) {
            return true;
        }

        return false;
    }

    private boolean handleModifyValue(UserSession session) {
        if (session.getState() == ConversationState.WAITING_NAME ||
                session.getState() == ConversationState.WAITING_STATUS ||
                session.getState() == ConversationState.WAITING_WORKED ||
                session.getState() == ConversationState.WAITING_EXPECTED) {

            Long taskId;

            try {
                taskId = Long.parseLong(session.getTaskId());
            } catch (Exception e) {
                send("🚨 Invalid task ID. Use only numbers.");
                session.resetConversation();
                return true;
            }

            try {
                AppUser user = joinCodeService.getUserByTelegramChatId(chatId);

                UpdateTaskRequest update = new UpdateTaskRequest();

                switch (session.getState()) {
                    case WAITING_NAME:
                        update.setTitle(requestText.trim());
                        break;

                    case WAITING_STATUS:
                        String status = requestText.trim().toLowerCase();

                        if (!status.equals("todo") &&
                                !status.equals("in-progress") &&
                                !status.equals("done")) {
                            send("🚨 Invalid status. Use: todo, in-progress, or done.");
                            return true;
                        }

                        update.setStatus(status);
                        break;

                    case WAITING_WORKED:
                        update.setWorkedHours(Integer.parseInt(requestText.trim()));
                        break;

                    case WAITING_EXPECTED:
                        update.setStoryPoints(Integer.parseInt(requestText.trim()));
                        break;

                    default:
                        return false;
                }

                taskService.updateTask(user.getId(), taskId, update);

                send("✅ Task updated successfully!");
                session.resetConversation();
                return true;

            } catch (NumberFormatException e) {
                send("🚨 Please write a valid number.");
                return true;
            } catch (Exception e) {
                logger.error("Error updating task from Telegram", e);
                send("🚨 Task could not be updated: " + e.getMessage());
                session.resetConversation();
                return true;
            }
        }

        if (session.getState() == ConversationState.CREATING_NAME) {
            session.setTempName(requestText.trim());
            session.setState(ConversationState.CREATING_EXPECTED);
            send(BotMessages.NEW_TASK_2.getMessage());
            return true;
        }

        if (session.getState() == ConversationState.CREATING_EXPECTED) {
            try {
                int expected = Integer.parseInt(requestText.trim());

                AppUser user = joinCodeService.getUserByTelegramChatId(chatId);

                TaskDTO createdTask = taskService.createTaskFromTelegram(
                        user.getTelegramUsername(),
                        session.getTempName(),
                        expected
                );

                String message = BotMessages.NEW_TASK_SUCC.getMessage()
                        + "\n🆔 ID: " + createdTask.getId()
                        + "\n📝 Title: " + createdTask.getTitle()
                        + "\n📁 Project ID: " + createdTask.getProjectId();

                send(message);
                session.resetConversation();

            } catch (NumberFormatException e) {
                send("🚨 Expected hours must be a valid number.");
            } catch (Exception e) {
                logger.error("Error creating task from Telegram", e);
                send("🚨 Task could not be created: " + e.getMessage());
                session.resetConversation();
            }

            return true;
        }

        return false;
    }

    public void fnConfigUser() {
        if (!requestText.equals(BotCommands.CONFIG_USER.getCommand()) || exit) {
            return;
        }

        UserSession session = getSession();
        session.setState(ConversationState.CONFIG_WAITING_CODE);

        send(BotMessages.CONFIG_USER_1.getMessage());
        exit = true;
    }

    public void fnStart() {
        if (!(requestText.equals(BotCommands.START_COMMAND.getCommand()) || requestText.equals("/start")) || exit) {
            return;
        }

        send(BotMessages.HELLO_MYTODO_BOT.getMessage());
        exit = true;
    }

    public void fnModfiyTask() {
        if (!requestText.equals(BotCommands.MOD_TASK.getCommand()) || exit) {
            return;
        }

        send(BotMessages.MOD_TASK_FNS.getMessage());
        exit = true;
    }

    public void fnModName() {
        if (!requestText.equals(BotCommands.MOD_NAME.getCommand()) || exit) {
            return;
        }

        UserSession session = getSession();
        session.setAction(UserAction.MOD_NAME);
        session.setState(ConversationState.WAITING_ID);

        send(BotMessages.MOD_ASK_ID.getMessage());
        exit = true;
    }

    public void fnModStatus() {
        if (!requestText.equals(BotCommands.MOD_STATUS.getCommand()) || exit) {
            return;
        }

        UserSession session = getSession();
        session.setAction(UserAction.MOD_STATUS);
        session.setState(ConversationState.WAITING_ID);

        send(BotMessages.MOD_ASK_ID.getMessage());
        exit = true;
    }

    public void fnModWorked() {
        if (!requestText.equals(BotCommands.MOD_WORKED.getCommand()) || exit) {
            return;
        }

        UserSession session = getSession();
        session.setAction(UserAction.MOD_WORKED);
        session.setState(ConversationState.WAITING_ID);

        send(BotMessages.MOD_ASK_ID.getMessage());
        exit = true;
    }

    public void fnModExpected() {
        if (!requestText.equals(BotCommands.MOD_EXPECTED.getCommand()) || exit) {
            return;
        }

        UserSession session = getSession();
        session.setAction(UserAction.MOD_EXPECTED);
        session.setState(ConversationState.WAITING_ID);

        send(BotMessages.MOD_ASK_ID.getMessage());
        exit = true;
    }

    public void fnNewTask() {
        if (!requestText.equals(BotCommands.NEW_TASK.getCommand()) || exit) {
            return;
        }

        try {
            joinCodeService.getUserByTelegramChatId(chatId);

            UserSession session = getSession();
            session.setAction(UserAction.CREATE_TASK);
            session.setState(ConversationState.CREATING_NAME);

            send(BotMessages.NEW_TASK_1.getMessage());
        } catch (Exception e) {
            send("🚨 Your Telegram account is not linked. Use /ConfigUser first.");
        }

        exit = true;
    }

    public void fnListMyTasks() {
        if (!requestText.equals(BotCommands.MY_TASKS.getCommand()) || exit) {
            return;
        }

        try {
            AppUser user = joinCodeService.getUserByTelegramChatId(chatId);

            List<TaskDTO> tasks = taskService.getTasksForUser(user.getId());

            if (tasks == null || tasks.isEmpty()) {
                send(BotMessages.MY_TASK_FAIL.getMessage());
                exit = true;
                return;
            }

            StringBuilder msg = new StringBuilder(BotMessages.MY_TASK_SUCC.getMessage());

            for (TaskDTO task : tasks) {
                msg.append("\n🆔 ID: ").append(task.getId())
                        .append("\n📝 Title: ").append(task.getTitle())
                        .append("\n📌 Status: ").append(task.getStatus())
                        .append("\n⏱️ Expected: ").append(task.getStoryPoints())
                        .append("\n");
            }

            send(msg.toString());

        } catch (Exception e) {
            logger.error("Error listing tasks from Telegram", e);
            send("🚨 Your Telegram account is not linked. Use /ConfigUser first.");
        }

        exit = true;
    }

    public void fnElse() {
        if (exit) {
            return;
        }

        UserSession session = getSession();

        if (session.getState() != ConversationState.NONE) {
            return;
        }

        send(BotMessages.HELLO_MYTODO_BOT.getMessage());
        exit = true;
    }
}
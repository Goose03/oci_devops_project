package com.springboot.MyTodoList.util;

import com.springboot.MyTodoList.dto.TaskDTO;
import com.springboot.MyTodoList.model.ToDoItem;
import com.springboot.MyTodoList.service.DeepSeekService;
import com.springboot.MyTodoList.service.TaskService;
import com.springboot.MyTodoList.service.ToDoItemService;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class BotActions {

    private static final Logger logger = LoggerFactory.getLogger(BotActions.class);

    String requestText;
    long chatId;
    TelegramClient telegramClient;
    boolean exit;

    ToDoItemService todoService;
    DeepSeekService deepSeekService;
    TaskService taskService;
    String telegramUsername;

    private static final Map<Long, UserSession> sessions = new HashMap<>();

    public BotActions(TelegramClient tc, ToDoItemService ts, DeepSeekService ds, TaskService taskService) {
        telegramClient = tc;
        todoService = ts;
        deepSeekService = ds;
        this.taskService = taskService;
        exit = false;
    }

    // ── Setters ───────────────────────────────────────────────────

    public void setRequestText(String cmd) {
        requestText = cmd;
    }

    public void setChatId(long chId) {
        chatId = chId;
    }

    public void setTelegramClient(TelegramClient tc) {
        telegramClient = tc;
    }

    public void setTodoService(ToDoItemService tsvc) {
        todoService = tsvc;
    }

    public ToDoItemService getTodoService() {
        return todoService;
    }

    public void setDeepSeekService(DeepSeekService dssvc) {
        deepSeekService = dssvc;
    }

    public DeepSeekService getDeepSeekService() {
        return deepSeekService;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public TaskService getTaskService() {
        return taskService;
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

    public void fnListMyTasks() {
        if (!(requestText.equals(BotCommands.MY_TASKS.getCommand())) || exit)
            return;

        List<ToDoItem> allItems = todoService.findAll();
        StringBuilder msg = new StringBuilder(BotMessages.MY_TASK_SUCC.getMessage());

        for (ToDoItem item : allItems) {
            // pendiente adaptar esta parte al modelo real de Task
        }

        BotHelper.sendMessageToTelegram(chatId, msg.toString(), telegramClient, null);
        exit = true;
    }

    public void fnElse() {
        if (exit) return;
        UserSession session = getSession();

        if (session.getState() != ConversationState.NONE)
            return;

        BotHelper.sendMessageToTelegram(chatId, BotMessages.HELLO_MYTODO_BOT.getMessage(), telegramClient, null);
    }

    public boolean handleState() {
        UserSession session = getSession();

        if (session.getState() == ConversationState.WAITING_ID) {

            session.setTaskId(requestText);

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
                return false;
        }
        return true;
        }
        return false;
    }

    /**
     * Modify flow step 2 — user provides the new value, we call TaskService.
     */
    private boolean handleModifyValue(UserSession session) {
        Long taskId;
        try {
            taskId = Long.parseLong(session.getTaskId());
        } catch (NumberFormatException e) {
            send("🚨 Invalid task ID: \"" + session.getTaskId() + "\". Please use a numeric ID.");
            session.resetConversation();
            return true;
        }

        if (session.getState() != ConversationState.NONE &&
            session.getAction() != UserAction.CREATE_TASK) {

        switch (session.getAction()) {

                case MOD_NAME:
                    break;

                case MOD_STATUS:
                    break;

                case MOD_WORKED:
                    break;

                case MOD_EXPECTED:
                    break;

            default:
                return false;
        }

            session.setState(ConversationState.NONE);
            session.setAction(UserAction.NONE);

            BotHelper.sendMessageToTelegram(chatId, "✅ Task updated successfully!", telegramClient, null);

            return true;
        }

        if (session.getState() == ConversationState.CREATING_NAME) {

            session.setTempName(requestText);
            session.setState(ConversationState.CREATING_EXPECTED);

            BotHelper.sendMessageToTelegram(chatId, "⏱️ Please write expected hours", telegramClient, null);

            return true;
        }

        if (session.getState() == ConversationState.CREATING_EXPECTED) {
            try {
                int expected = Integer.parseInt(requestText);
                session.setTempExpected(expected);

                TaskDTO createdTask = taskService.createTaskFromTelegram(
                        telegramUsername,
                        session.getTempName(),
                        expected
                );

                session.setTempName(null);
                session.setTempExpected(null);
                session.setState(ConversationState.NONE);
                session.setAction(UserAction.NONE);

                String message = BotMessages.NEW_TASK_SUCC.getMessage()
                        + "\n🆔 ID: " + createdTask.getId()
                        + "\n📝 Title: " + createdTask.getTitle()
                        + "\n📁 Project ID: " + createdTask.getProjectId();

                BotHelper.sendMessageToTelegram(chatId, message, telegramClient, null);

            } catch (NumberFormatException e) {
                BotHelper.sendMessageToTelegram(chatId, "🚨 Expected hours must be a valid number!", telegramClient, null);
            } catch (Exception e) {
                logger.error("Error creating task from Telegram", e);
                BotHelper.sendMessageToTelegram(chatId, "🚨 Task could not be created!\n" + e.getMessage(), telegramClient, null);
            }

            return true;
        }

        return false;
    }

    public void fnStart() {
        if (!(requestText.equals(BotCommands.START_COMMAND.getCommand())) || exit)
            return;

        BotHelper.sendMessageToTelegram(chatId, BotMessages.HELLO_MYTODO_BOT.getMessage(), telegramClient, null);

        exit = true;
    }

    public void fnModfiyTask() {
        if (!(requestText.equals(BotCommands.MOD_TASK.getCommand())) || exit)
            return;

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_TASK_FNS.getMessage(), telegramClient, null);

        exit = true;
    }

    public void fnModifyCommands() {
        UserSession session = getSession();

        if (requestText.equals(BotCommands.MOD_NAME.getCommand())) {
            session.setAction(UserAction.MOD_NAME);

        } else if (requestText.equals(BotCommands.MOD_STATUS.getCommand())) {
            session.setAction(UserAction.MOD_STATUS);

        } else if (requestText.equals(BotCommands.MOD_WORKED.getCommand())) {
            session.setAction(UserAction.MOD_WORKED);

        } else if (requestText.equals(BotCommands.MOD_EXPECTED.getCommand())) {
            session.setAction(UserAction.MOD_EXPECTED);

        } else {
            return;
        }

        session.setState(ConversationState.WAITING_ID);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_ASK_ID.getMessage(), telegramClient, null);
    }

    public void fnModName() {
        if (!requestText.equals(BotCommands.MOD_NAME.getCommand()))
            return;

        UserSession session = getSession();
        session.setAction(UserAction.MOD_NAME);
        session.setState(ConversationState.WAITING_ID);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_ASK_ID.getMessage(), telegramClient, null);
    }

    public void fnModStatus() {
        if (!requestText.equals(BotCommands.MOD_STATUS.getCommand()))
            return;

        UserSession session = getSession();
        session.setAction(UserAction.MOD_STATUS);
        session.setState(ConversationState.WAITING_ID);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_ASK_ID.getMessage(), telegramClient, null);
    }

    public void fnModWorked() {
        if (!requestText.equals(BotCommands.MOD_WORKED.getCommand()))
            return;

        UserSession session = getSession();
        session.setAction(UserAction.MOD_WORKED);
        session.setState(ConversationState.WAITING_ID);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_ASK_ID.getMessage(), telegramClient, null);
    }

    public void fnModExpected() {
        if (!requestText.equals(BotCommands.MOD_EXPECTED.getCommand()))
            return;

        UserSession session = getSession();
        session.setAction(UserAction.MOD_EXPECTED);
        session.setState(ConversationState.WAITING_ID);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.MOD_ASK_ID.getMessage(), telegramClient, null);
    }

    public void fnNewTask() {
        if (!requestText.equals(BotCommands.NEW_TASK.getCommand()))
            return;

        UserSession session = getSession();

        session.setAction(UserAction.CREATE_TASK);
        session.setState(ConversationState.CREATING_NAME);

        BotHelper.sendMessageToTelegram(chatId, BotMessages.NEW_TASK_1.getMessage(), telegramClient, null);
    }
}

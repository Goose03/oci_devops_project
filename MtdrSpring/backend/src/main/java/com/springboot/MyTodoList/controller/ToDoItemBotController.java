package com.springboot.MyTodoList.controller;

import com.springboot.MyTodoList.config.BotProps;
import com.springboot.MyTodoList.service.DeepSeekService;
import com.springboot.MyTodoList.service.JoinCodeService;
import com.springboot.MyTodoList.service.TaskService;
import com.springboot.MyTodoList.service.ToDoItemService;
import com.springboot.MyTodoList.util.BotActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class ToDoItemBotController implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ToDoItemBotController.class);

    private final ToDoItemService toDoItemService;
    private final DeepSeekService deepSeekService;
    private final TaskService taskService;
    private final JoinCodeService joinCodeService;
    private final TelegramClient telegramClient;
    private final BotProps botProps;

    public ToDoItemBotController(
            BotProps botProps,
            ToDoItemService toDoItemService,
            DeepSeekService deepSeekService,
            TaskService taskService,
            JoinCodeService joinCodeService
    ) {
        this.botProps = botProps;
        this.toDoItemService = toDoItemService;
        this.deepSeekService = deepSeekService;
        this.taskService = taskService;
        this.joinCodeService = joinCodeService;
        this.telegramClient = new OkHttpTelegramClient(botProps.getToken());
    }

    @Override
    public String getBotToken() {
        return botProps.getToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();

        String telegramUsername = null;
        if (update.getMessage().getFrom() != null) {
            telegramUsername = update.getMessage().getFrom().getUserName();
        }

        BotActions actions = new BotActions(
                telegramClient,
                toDoItemService,
                deepSeekService,
                taskService,
                joinCodeService
        );

        actions.setRequestText(messageText);
        actions.setChatId(chatId);
        actions.setTelegramUsername(telegramUsername);

        if (actions.handleState()) {
            return;
        }

        actions.fnConfigUser();
        actions.fnStart();
        actions.fnModfiyTask();
        actions.fnModName();
        actions.fnModStatus();
        actions.fnModWorked();
        actions.fnModExpected();
        actions.fnNewTask();

        actions.fnListMyTasks();
        actions.fnListMyTasksAll();
        actions.fnListMyTasksTodo();
        actions.fnListMyTasksProgress();
        actions.fnListMyTasksDone();

        actions.fnElse();
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        logger.info("Registered bot running state is: {}", botSession.isRunning());
    }
}
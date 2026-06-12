package com.springboot.MyTodoList.util;

public enum BotCommands {

    START_COMMAND("/JTDI"),
    CONFIG_USER("/ConfigUser"),
    TASK_FUNCS("/TaskFunctions"),

    MY_TASKS("/MyTasks"),
    MY_TASKS_ALL("/MyTasksAll"),
    MY_TASKS_TODO("/MyTasksTodo"),
    MY_TASKS_PROGRESS("/MyTasksProgress"),
    MY_TASKS_DONE("/MyTasksDone"),

    ALL_TASKS("/AllTasks"),
    MOD_TASK("/ModifyTask"),
    MOD_NAME("/ModName"),
    MOD_STATUS("/ModStatus"),
    MOD_WORKED("/ModWorked"),
    MOD_EXPECTED("/ModExpected"),
    NEW_TASK("/NewTask"),
    SHOW_FUNCS("/fns");

    private final String command;

    BotCommands(String enumCommand) {
        this.command = enumCommand;
    }

    public String getCommand() {
        return command;
    }
}
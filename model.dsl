workspace {

model {

    user = person "User"
    admin = person "Administrator"

    telegramApi = softwareSystem "Telegram API"

    justDoIt = softwareSystem "JustDoIt Platform" {

        webApp = container "Web Application" "React frontend" "React"

        taskApi = container "Task API" "Backend API" "Spring Boot" {

            taskController = component "Task Controller"
            authController = component "Auth Controller"

            taskService = component "Task Service"
            authService = component "Auth Service"

            taskRepository = component "Task Repository"
            userRepository = component "User Repository"

            taskController -> taskService "Uses"
            authController -> authService "Uses"

            taskService -> taskRepository "Uses"
            authService -> userRepository "Uses"
        }

        telegramBot = container "Telegram Bot" "Telegram integration" "Bot"

        redis = container "Redis Cache" "Cache" "Redis"

        database = container "Oracle Database" "Persistence" "Oracle"
    }

    user -> webApp "Uses"

    admin -> justDoIt "Administers"

    webApp -> taskApi "HTTPS"

    telegramBot -> telegramApi "Uses Telegram API"

    telegramBot -> taskApi "Calls API"

    taskApi -> redis "Uses cache"

    taskApi -> database "Reads/Writes"

    deploymentEnvironment "Production" {

        deploymentNode "OCI Cloud" {

            deploymentNode "Application Server" {
                webAppInstance = containerInstance webApp
                taskApiInstance = containerInstance taskApi
                telegramBotInstance = containerInstance telegramBot
            }

            deploymentNode "Cache Server" {
                redisInstance = containerInstance redis
            }

            deploymentNode "Database Server" {
                databaseInstance = containerInstance database
            }
        }
    }
}

views {

    systemLandscape {
        include *
        autolayout lr
    }

    systemContext justDoIt {
        include *
        autolayout lr
    }

    container justDoIt {
        include *
        autolayout lr
    }

    component taskApi {
        include *
        autolayout lr
    }

    deployment * Production {
        include *
        autolayout lr
    }

    dynamic justDoIt {

        user -> webApp "Create task"
        webApp -> taskApi "Send request"
        taskApi -> database "Store task"

        autolayout lr
    }

    theme default
}

}
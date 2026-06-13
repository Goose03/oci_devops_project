workspace "MyTodoList - C4 Model" "Arquitectura C4 del sistema MyTodoList" {

    model {
        user = person "Usuario" "Persona que administra proyectos, sprints y tareas."

        softwareSystem = softwareSystem "MyTodoList" "Sistema web para gestión de tareas, proyectos, sprints, equipos, analíticas, IA y bot de Telegram." {
            
            webApp = container "Web App" "Interfaz web usada por el usuario." "React / Vite"

            backend = container "Backend API" "Expone servicios REST y contiene la lógica principal del sistema." "Java Spring Boot" {
                
                component "Auth Component" {
                    description "Gestiona registro, inicio de sesión, JWT y seguridad."
                    technology "Spring Boot Security / JWT"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Task Component" {
                    description "Gestiona creación, consulta, actualización y eliminación de tareas."
                    technology "Java Spring Boot"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Project Component" {
                    description "Gestiona proyectos asociados a usuarios y equipos."
                    technology "Java Spring Boot"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Sprint Component" {
                    description "Gestiona los sprints del proyecto."
                    technology "Java Spring Boot"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Team Component" {
                    description "Gestiona equipos, invitaciones y membresías."
                    technology "Java Spring Boot"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Analytics Component" {
                    description "Calcula KPIs, distribución de tareas, velocidad y horas trabajadas."
                    technology "Java Spring Boot"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "AI Insights Component" {
                    description "Genera análisis e insights usando servicios de inteligencia artificial."
                    technology "Java Spring Boot / AI API"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }

                component "Telegram Bot Component" {
                    description "Permite vincular usuarios y consultar tareas desde Telegram."
                    technology "Telegram Bot API / Java"
                    url "https://github.com/Goose03/oci_devops_project/blob/main/docs/diagrams/backend-code-level4.puml"
                }
            }

            database = container "Oracle Database" "Almacena usuarios, tareas, proyectos, sprints, equipos, códigos de unión e insights." "Oracle Database"
        }

        user -> webApp "Usa"
        webApp -> backend "Consume API REST"
        backend -> database "Lee y escribe datos"
    }

    views {
        systemContext softwareSystem {
            include *
            autolayout lr
        }

        container softwareSystem {
            include *
            autolayout lr
        }

        component backend {
            include *
            autolayout lr
        }

        theme default
    }
}
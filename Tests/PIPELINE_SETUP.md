# Test Pipeline Setup Guide

## Prerequisitos

- Acceso a OCI Console con el proyecto DevOps ya configurado
- Git instalado localmente
- Python 3.8+ instalado localmente
- El build+deploy pipeline ya funcionando (el principal)

---

## Parte 1 — Configurar localmente

### 1.1 Crear el archivo `.env`

Crea el archivo `Tests/.env` (nunca lo commitees a git):

```
APP_URL=http://140.84.190.22
VALID_EMAIL=mariaguadalupesotoa@gmail.com
VALID_PASSWORD=123

DASHBOARD_URL=http://140.84.190.22
DASHBOARD_EMAIL=mariaguadalupesotoa@gmail.com
DASHBOARD_PASS=123

JIRA_URL=https://tec-team-kretou7k.atlassian.net
JIRA_EMAIL=A00228158@tec.mx
JIRA_TOKEN=ATATT3xFfGF0PM76w081XxfzxADwtAQY4n8_htlvO57QSRT8OJ9ExTW6gplJe1wOu-M1pJUmg6X25T8kmJmoH0jOA-PDqM2e2EMVSDcIyTKx9UwINlHqC-Nl0Bp4oDiSLoUa6QGgXG-9Daw8VKDQTbC87eQr7sCEFpjhrXQlXXDeXvjLv3BkEcQ=3B2F2280
JIRA_PROJECT=SWE
```

### 1.2 Instalar dependencias

```bash
cd Tests
pip install pytest selenium webdriver-manager python-dotenv requests
```

### 1.3 Correr tests localmente

```bash
# Desde la carpeta Tests/
cd Tests

# Solo login
pytest tests/Login -v

# Solo WebApp
pytest tests/WebApp -v

# Solo Dashboard KPIs
pytest tests/Dashboard -v

# Dashboard flow completo (secuencial)
pytest pages/dashboard/test_dashboard_flow.py -v

# Todos
pytest tests/ -v --junitxml=test-results.xml
```

### 1.4 Verificar tickets JIRA (después de una corrida con fallos)

```bash
python utils/create_jira_ticket.py test-results.xml
```

---

## Parte 2 — Crear el pipeline de tests en OCI

### 2.1 Crear el Build Pipeline

1. OCI Console → DevOps → tu proyecto → **Build Pipelines**
2. Clic en **Create Build Pipeline**
3. Llena:
   - **Name**: `test-pipeline`
   - **Description**: Selenium test pipeline
4. Clic **Create**

### 2.2 Agregar el stage de build

1. Dentro del pipeline recién creado, clic en el **"+"**
2. Selecciona **Managed Build**
3. Llena:
   - **Stage name**: `RunTests`
   - **Build spec file path**: `build_spec_tests.yaml`
   - **Primary code repository**: selecciona tu repo (el mismo que usa el build pipeline principal)
4. Clic **Add**

### 2.3 Agregar los parámetros secretos

1. En el pipeline → pestaña **Parameters**
2. Agrega cada uno con el botón **+**:

| Name | Value | Type |
|------|-------|------|
| `DASHBOARD_EMAIL` | `mariaguadalupesotoa@gmail.com` | String |
| `DASHBOARD_PASS` | `123` | String |
| `JIRA_TOKEN` | `ATATT3xFfGF0PM76w081X...` (completo) | String |
| `JIRA_EMAIL` | `A00228158@tec.mx` | String |
| `JIRA_PROJECT` | `SWE` | String |
| `DASHBOARD_URL` | `http://140.84.190.22` | String |

> **Nota**: En OCI los parámetros se pasan como env vars al build spec automáticamente.

### 2.4 Probar el pipeline manualmente

1. En el pipeline → clic **Run Pipeline**
2. Deja los parámetros por default
3. Clic **Run**
4. Espera y verifica que el stage `RunTests` pase ✅
5. Revisa los logs para ver el output de pytest

---

## Parte 3 — Crear el Trigger para Pull Requests

### 3.1 Crear el trigger

1. OCI Console → DevOps → tu proyecto → **Triggers**
2. Clic **Create Trigger**
3. Llena:
   - **Name**: `pr-test-trigger`
   - **Description**: Runs Selenium tests on PRs to main
   - **Source connection**: selecciona tu conexión de GitHub
   - **Repository**: tu repo
4. En **Actions**:
   - **Build pipeline**: selecciona `test-pipeline`
   - **Events**: marca **Pull Request Opened** y **Pull Request Synchronized**
   - **Source branch filter**: `main` (la rama destino del PR)
5. Clic **Create**

---

## Parte 4 — Flujo de trabajo diario

### Cuando quieras agregar nuevos tests:

```bash
# 1. Crea tu rama
git checkout -b feature/nuevo-test

# 2. Agrega tu test en Tests/tests/
# 3. Commitea
git add Tests/
git commit -m "Add test for [feature]"
git push origin feature/nuevo-test

# 4. Abre un Pull Request en GitHub hacia main
# → OCI detecta el PR automáticamente
# → Corre el test-pipeline
# → Si pasan: merge y el build+deploy pipeline se activa
# → Si fallan: ves el error en OCI y se crea ticket en JIRA
```

### Flujo completo end-to-end:

```
git push (rama) 
    → PR abierto en GitHub
        → OCI pr-test-trigger detecta el PR
            → test-pipeline corre pytest (headless Chrome en OCI)
                → ✅ Tests pasan → puedes hacer merge
                → ❌ Tests fallan → ticket en JIRA automáticamente
                    → merge bloqueado visualmente
    → merge a main
        → build-pipeline corre (build + deploy)
            → nueva versión en http://140.84.190.22
```

---

## Parte 5 — Solución de problemas comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `ChromeDriver not found` | Chrome no instalado en OCI build runner | El `build_spec_tests.yaml` lo instala automáticamente |
| `DASHBOARD_EMAIL not set` | Parámetros no configurados en el pipeline | Revisar Parte 2.3 |
| `Could not find element` | Selector incorrecto o app caída | Verificar que `http://140.84.190.22` responde |
| `JIRA 401 Unauthorized` | Token inválido o expirado | Generar nuevo token en Atlassian → Account Settings → Security → API tokens |
| `ConnectionRefusedError` | App no corriendo | Hacer `kubectl rollout restart` en Cloud Shell |
| Tests de Dashboard flow fallan en test_02+ | Sesión no persistió entre tests | El fixture `page` es class-scoped, verificar que pytest no esté recreando la clase |

---

## Estructura de archivos de tests

```
Tests/
├── .env                          ← credenciales locales (NO en git)
├── conftest.py                   ← fixtures: driver, page
├── pytest.ini                    ← config de pytest
├── requirements.txt              ← dependencias
├── pages/
│   ├── base_page.py
│   ├── login/
│   │   ├── LoginFunctions.py
│   │   └── LoginSelectors.py
│   ├── webapp/
│   │   ├── TaskFunctions.py
│   │   └── TaskSelectors.py
│   └── dashboard/
│       ├── __init__.py           ← Selectors + DashboardPage
│       └── test_dashboard_flow.py
├── tests/
│   ├── Login/
│   │   └── test_Login.py         ← 5 casos
│   ├── WebApp/
│   │   └── test_CreateTask.py    ← 3 casos
│   └── Dashboard/
│       └── dashboard.py          ← 4 casos KPI
└── utils/
    ├── wait_utils.py
    └── create_jira_ticket.py     ← abre bugs en JIRA automáticamente
```

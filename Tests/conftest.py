import pytest
import os
import tempfile
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager


def _build_chrome_options(headless=False):
    options = webdriver.ChromeOptions()
    if headless:
        options.add_argument("--headless=new")
        options.add_argument("--window-size=1920,1080")
    else:
        options.add_argument("--start-maximized")

    # Always use a dedicated temp profile to avoid conflicts with an open Chrome
    profile_dir = os.path.join(tempfile.gettempdir(), "chrome-selenium-profile")
    options.add_argument(f"--user-data-dir={profile_dir}")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-extensions")
    options.add_argument("--remote-debugging-port=0")
    return options


@pytest.fixture
def driver():
    headless = os.environ.get("HEADLESS", "false").lower() == "true"
    options = _build_chrome_options(headless=headless)
    drv = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options,
    )
    yield drv
    drv.quit()


@pytest.fixture(scope="class")
def page():
    """
    Class-scoped fixture that provides a logged-in DashboardPage instance.
    Tests in the same class share the same browser session, allowing
    sequential flows (create team → project → task → move).

    Env vars:
      DASHBOARD_URL   — app base URL (default: http://140.84.190.22)
      DASHBOARD_EMAIL — test user email
      DASHBOARD_PASS  — test user password
      HEADLESS        — set to "true" for CI (default: false)
    """
    from pages.dashboard import DashboardPage

    base_url = os.environ.get("DASHBOARD_URL", "http://140.84.190.22")
    email    = os.environ.get("DASHBOARD_EMAIL", "")
    password = os.environ.get("DASHBOARD_PASS", "")
    headless = os.environ.get("HEADLESS", "false").lower() == "true"

    options = _build_chrome_options(headless=headless)
    drv = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options,
    )

    pg = DashboardPage(drv, base_url=base_url)
    if email and password:
        pg.login(email, password)

    yield pg

    drv.quit()
# Page object for the web app Login page

from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from utils.wait_utils import wait_for_element, wait_for_clickable, wait_seconds
from pages.base_page import BasePage
from pages.login.LoginSelectors import (
    LOGIN_EMAIL,
    LOGIN_PASSWORD,
    LOGIN_BUTTON,
    LOGIN_TOAST_ERROR,
    DASHBOARD_INDICATOR,
)


class Login(BasePage):
    def __init__(self, driver):
        self.driver = driver

    def open(self, url):
        # Navigate to root so the app loads and redirects to /login automatically.
        # Going directly to /login can cause a 403 on some server configs.
        base = url.replace("/login", "").rstrip("/")
        self.driver.get(base)
        wait_seconds(2)
        # If the app didn't redirect to /login, navigate there explicitly
        if "/login" not in self.driver.current_url:
            self.driver.get(url)
            wait_seconds(2)

    def enter_email(self, email):
        field = wait_for_element(self.driver, LOGIN_EMAIL)
        field.clear()
        field.send_keys(email)

    def enter_password(self, password):
        field = wait_for_element(self.driver, LOGIN_PASSWORD)
        field.clear()
        field.send_keys(password)

    def click_login(self):
        btn = wait_for_clickable(self.driver, LOGIN_BUTTON)
        btn.click()
        wait_seconds(3)

    def login(self, email, password):
        self.enter_email(email)
        self.enter_password(password)
        self.click_login()

    def is_logged_in(self, timeout=8):
        try:
            WebDriverWait(self.driver, timeout).until(
                EC.presence_of_element_located(DASHBOARD_INDICATOR)
            )
            return True
        except Exception:
            return False

    def get_toast_error(self):
        try:
            el = wait_for_element(self.driver, LOGIN_TOAST_ERROR, timeout=5)
            return el.text
        except Exception:
            return ""

    def current_url(self):
        return self.driver.current_url
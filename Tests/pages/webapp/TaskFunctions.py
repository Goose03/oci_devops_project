from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from selenium.common.exceptions import WebDriverException

from utils.wait_utils import wait_for_element, wait_for_clickable, wait_seconds
from pages.base_page import BasePage
from pages.webapp.TaskSelectors import (
    NEW_TASK_BUTTON,
    TASK_TITLE,
    TASK_DESCRIPTION,
    TASK_HOURS,
    PROJECT_TRIGGER,
    SUBMIT_BUTTON,
    DIALOG_TITLE,
    task_card_by_title,
)


class TaskPage(BasePage):
    def __init__(self, driver):
        self.driver = driver

    def open(self, url, retries=3):
        for attempt in range(1, retries + 1):
            try:
                self.driver.get(url)
                wait_seconds(5)
                return
            except WebDriverException as e:
                if attempt < retries:
                    print(f"[open] attempt {attempt} failed ({e.msg[:60]}), retrying in 8s…")
                    wait_seconds(8)
                else:
                    raise

    def click_new_task(self):
        try:
            btn = wait_for_clickable(self.driver, NEW_TASK_BUTTON, timeout=15)
        except Exception:
            btn = wait_for_clickable(
                self.driver,
                (By.XPATH, "//button[contains(normalize-space(),'New Task')]"),
                timeout=15
            )
        btn.click()
        wait_seconds(3)

    def fill_title(self, title):
        field = wait_for_element(self.driver, TASK_TITLE, timeout=20)
        field.clear()
        field.send_keys(title)

    def fill_description(self, description):
        field = wait_for_element(self.driver, TASK_DESCRIPTION, timeout=10)
        field.clear()
        field.send_keys(description)

    def fill_hours(self, hours):
        field = wait_for_element(self.driver, TASK_HOURS, timeout=10)
        field.clear()
        field.send_keys(str(hours))

    def select_project(self, project_name):
        # Abrir el dropdown
        trigger = wait_for_clickable(self.driver, PROJECT_TRIGGER, timeout=10)
        trigger.click()
        wait_seconds(1)

        # Las opciones de shadcn/ui se renderizan en un portal
        # Buscar por texto visible usando contains() para ignorar el key entre paréntesis
        option_selectors = [
            (By.XPATH, f"//*[@role='option' and contains(normalize-space(),'{project_name}')]"),
            (By.XPATH, f"//div[@role='listbox']//*[contains(normalize-space(),'{project_name}')]"),
            (By.XPATH, f"//*[contains(@class,'SelectItem') and contains(normalize-space(),'{project_name}')]"),
            (By.XPATH, f"//*[contains(@class,'select-item') and contains(normalize-space(),'{project_name}')]"),
        ]

        for selector in option_selectors:
            try:
                option = WebDriverWait(self.driver, 5).until(
                    EC.element_to_be_clickable(selector)
                )
                option.click()
                wait_seconds(1)
                return
            except Exception:
                continue

        # Último recurso: JavaScript
        clicked = self.driver.execute_script(f"""
            var items = document.querySelectorAll('[role="option"]');
            for (var item of items) {{
                if (item.textContent.includes('{project_name}')) {{
                    item.click();
                    return true;
                }}
            }}
            return false;
        """)
        if not clicked:
            raise Exception(f"No se encontro la opcion '{project_name}' en el dropdown")
        wait_seconds(1)

    def submit(self):
        btn = wait_for_clickable(self.driver, SUBMIT_BUTTON, timeout=10)
        btn.click()
        wait_seconds(3)

    def dialog_still_open(self, timeout=3):
        """Verifica que el dialog sigue abierto (titulo 'Create New Task' visible)."""
        try:
            WebDriverWait(self.driver, timeout).until(
                EC.presence_of_element_located(DIALOG_TITLE)
            )
            return True
        except Exception:
            return False

    def is_task_visible(self, title, timeout=10):
        try:
            WebDriverWait(self.driver, timeout).until(
                EC.presence_of_element_located(task_card_by_title(title))
            )
            return True
        except Exception:
            return False
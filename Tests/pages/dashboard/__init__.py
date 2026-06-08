"""
Dashboard page object and selectors for Selenium tests.

Covers:
  - Sidebar team creation (New Group → Create Group dialog)
  - /projects page (New Project dialog)
  - /board page (New Task dialog, column assertions, drag-and-drop)
  - Edit dialog (move task via status change)
"""

import time
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage


# ─── SELECTORS ────────────────────────────────────────────────────────────────

class Selectors:
    # Auth
    EMAIL_INPUT    = (By.ID, "email")
    PASSWORD_INPUT = (By.ID, "password")
    LOGIN_BUTTON   = (By.CSS_SELECTOR, "button[type='submit']")

    # Sidebar — New Group dropdown
    NEW_GROUP_BTN       = (By.XPATH, "//button[.//span[contains(text(),'New Group')]]")
    CREATE_GROUP_ITEM   = (By.XPATH, "//p[text()='Create Group']")
    SB_TEAM_NAME        = (By.ID, "sb-teamName")
    SB_TEAM_DESC        = (By.ID, "sb-teamDesc")
    CREATE_GROUP_SUBMIT = (By.XPATH, "//button[@type='submit' and contains(.,'Create Group')]")

    # Projects page
    NEW_PROJECT_BTN       = (By.XPATH, "//button[contains(.,'New Project')]")
    PROJ_NAME_INPUT       = (By.ID, "create-name")
    PROJ_KEY_INPUT        = (By.ID, "create-key")
    PROJ_DESC_INPUT       = (By.ID, "create-description")
    CREATE_PROJECT_SUBMIT = (By.XPATH, "//button[@type='submit' and contains(.,'Create Project')]")

    # Board
    NEW_TASK_BTN       = (By.XPATH, "//button[contains(.,'New Task')]")
    TASK_TITLE_INPUT   = (By.ID, "td-title")
    CREATE_TASK_SUBMIT = (By.XPATH, "//button[@type='submit' and contains(.,'Create Task')]")
    SAVE_CHANGES_BTN   = (By.XPATH, "//button[@type='submit' and contains(.,'Save Changes')]")

    # Board columns (drop zones — Card element after column header)
    COL_TODO_ZONE        = (By.XPATH, "(//h3[normalize-space()='To Do']/parent::div/following-sibling::*)[1]")
    COL_IN_PROGRESS_ZONE = (By.XPATH, "(//h3[normalize-space()='In Progress']/parent::div/following-sibling::*)[1]")
    COL_DONE_ZONE        = (By.XPATH, "(//h3[normalize-space()='Done']/parent::div/following-sibling::*)[1]")


# ─── PAGE OBJECT ──────────────────────────────────────────────────────────────

class DashboardPage(BasePage):
    def __init__(self, driver, base_url="http://140.84.190.22"):
        super().__init__(driver)
        self.base_url = base_url

    # ── Navigation ────────────────────────────────────────────────────────────

    def go_to_dashboard(self):
        self.driver.get(f"{self.base_url}/dashboard")
        time.sleep(2)

    def go_to_projects(self):
        self.driver.get(f"{self.base_url}/projects")
        time.sleep(2)

    def go_to_board(self):
        self.driver.get(f"{self.base_url}/board")
        time.sleep(2)

    # ── Auth ──────────────────────────────────────────────────────────────────

    def login(self, email, password):
        self.driver.get(self.base_url)
        time.sleep(2)
        if "/login" not in self.driver.current_url:
            self.driver.get(f"{self.base_url}/login")
            time.sleep(2)
        self._wait_for_element(Selectors.EMAIL_INPUT).send_keys(email)
        self.driver.find_element(*Selectors.PASSWORD_INPUT).send_keys(password)
        self._wait_for_clickable(Selectors.LOGIN_BUTTON).click()
        time.sleep(3)

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _wait_for_element(self, locator, timeout=15):
        return WebDriverWait(self.driver, timeout).until(
            EC.presence_of_element_located(locator)
        )

    def _wait_for_clickable(self, locator, timeout=15):
        return WebDriverWait(self.driver, timeout).until(
            EC.element_to_be_clickable(locator)
        )

    def _wait_for_text(self, text, timeout=15):
        WebDriverWait(self.driver, timeout).until(
            EC.presence_of_element_located(
                (By.XPATH, f"//*[contains(text(),'{text}')]")
            )
        )

    def _click_radix_select(self, trigger_id, option_text, timeout=10):
        """Open a Radix UI <Select> and pick an option by visible text."""
        trigger = self._wait_for_clickable((By.ID, trigger_id), timeout)
        trigger.click()
        time.sleep(0.5)
        option = self._wait_for_clickable(
            (By.XPATH, f"//div[@role='option' and contains(normalize-space(),'{option_text}')]"),
            timeout,
        )
        option.click()
        time.sleep(0.3)

    # ── Team ──────────────────────────────────────────────────────────────────

    def create_team(self, name, description):
        self._wait_for_clickable(Selectors.NEW_GROUP_BTN).click()
        time.sleep(0.4)
        self._wait_for_clickable(Selectors.CREATE_GROUP_ITEM).click()
        time.sleep(0.5)

        name_field = self._wait_for_element(Selectors.SB_TEAM_NAME)
        name_field.clear()
        name_field.send_keys(name)

        desc_field = self.driver.find_element(*Selectors.SB_TEAM_DESC)
        desc_field.clear()
        desc_field.send_keys(description)

        self._wait_for_clickable(Selectors.CREATE_GROUP_SUBMIT).click()
        time.sleep(2)

    # ── Project ───────────────────────────────────────────────────────────────

    def create_project(self, name, key, team_name, description="", status="Active"):
        self._wait_for_clickable(Selectors.NEW_PROJECT_BTN).click()
        time.sleep(0.5)

        self._wait_for_element(Selectors.PROJ_NAME_INPUT).send_keys(name)
        self.driver.find_element(*Selectors.PROJ_KEY_INPUT).send_keys(key)
        if description:
            self.driver.find_element(*Selectors.PROJ_DESC_INPUT).send_keys(description)

        self._click_radix_select("create-team", team_name)
        self._click_radix_select("create-status", status)

        self._wait_for_clickable(Selectors.CREATE_PROJECT_SUBMIT).click()
        time.sleep(2)

    # ── Task ──────────────────────────────────────────────────────────────────

    def create_task(self, title):
        self._wait_for_clickable(Selectors.NEW_TASK_BTN).click()
        time.sleep(0.5)
        self._wait_for_element(Selectors.TASK_TITLE_INPUT).send_keys(title)
        self._wait_for_clickable(Selectors.CREATE_TASK_SUBMIT).click()
        time.sleep(2)

    def move_task_via_edit(self, task_title, new_status):
        task_card = self._wait_for_clickable(
            (By.XPATH,
             f"//*[contains(text(),'{task_title}')]"
             f"/ancestor::*[contains(@class,'cursor-pointer') or @role='button'][1]"),
            timeout=10,
        )
        task_card.click()
        time.sleep(1)
        self._click_radix_select("td-status", new_status)
        self._wait_for_clickable(Selectors.SAVE_CHANGES_BTN).click()
        time.sleep(2)

    def move_task_via_drag(self, task_title, target_col_locator):
        source = self._wait_for_element(
            (By.XPATH,
             f"//*[contains(text(),'{task_title}')]"
             f"/ancestor::*[@draggable='true'][1]"),
            timeout=10,
        )
        target = self._wait_for_element(target_col_locator)

        # React DnD (HTML5Backend) needs native drag events, not ActionChains.
        self.driver.execute_script(
            """
            const dt = new DataTransfer();
            arguments[0].dispatchEvent(new DragEvent('dragstart', {bubbles:true,cancelable:true,dataTransfer:dt}));
            arguments[1].dispatchEvent(new DragEvent('dragenter', {bubbles:true,cancelable:true,dataTransfer:dt}));
            arguments[1].dispatchEvent(new DragEvent('dragover',  {bubbles:true,cancelable:true,dataTransfer:dt}));
            arguments[1].dispatchEvent(new DragEvent('drop',      {bubbles:true,cancelable:true,dataTransfer:dt}));
            arguments[0].dispatchEvent(new DragEvent('dragend',   {bubbles:true,cancelable:true,dataTransfer:dt}));
            """,
            source,
            target,
        )
        time.sleep(1)

    def assert_task_in_column(self, task_title, column_name):
        # column_name may be "TO DO", "IN PROGRESS", "DONE" — match case-insensitively
        xpath = (
            f"//h3[contains("
            f"translate(normalize-space(text()),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"
            f",'{column_name}')]"
            f"/parent::div/following-sibling::*[1]"
            f"//*[contains(text(),'{task_title}')]"
        )
        elements = self.driver.find_elements(By.XPATH, xpath)
        assert len(elements) > 0, (
            f"Task '{task_title}' not found in column '{column_name}'"
        )

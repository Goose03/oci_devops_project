import os
import pytest
from dotenv import load_dotenv

from pages.login.LoginFunctions import Login
from utils.wait_utils import wait_seconds

load_dotenv()

APP_URL        = os.getenv("APP_URL", "http://localhost:3000")
VALID_EMAIL    = os.getenv("VALID_EMAIL")
VALID_PASSWORD = os.getenv("VALID_PASSWORD")
LOGIN_URL      = APP_URL + "/login"


def test_login_valid_credentials(driver):
    """TC-LOGIN-01: Login con credenciales correctas."""
    login = Login(driver)
    login.open(LOGIN_URL)
    login.login(VALID_EMAIL, VALID_PASSWORD)

    assert "dashboard" in login.current_url() or login.is_logged_in(), (
        f"❌ Expected dashboard. URL: {login.current_url()}"
    )
    print(f"✅ Login exitoso. URL: {login.current_url()}")


def test_login_wrong_password(driver):
    """TC-LOGIN-02: Login falla con contraseña incorrecta."""
    login = Login(driver)
    login.open(LOGIN_URL)
    login.login(VALID_EMAIL, "123")

    assert not login.is_logged_in(timeout=4), (
        "❌ No debe loguearse con contraseña incorrecta."
    )
    print("✅ Contraseña incorrecta rechazada correctamente.")


def test_login_wrong_email(driver):
    """TC-LOGIN-03: Login falla con email no registrado."""
    login = Login(driver)
    login.open(LOGIN_URL)
    login.login("notregistered_xyz@fake123.com", "SomePassword1!")

    assert not login.is_logged_in(timeout=4), (
        "❌ No debe loguearse con email no registrado."
    )
    print("✅ Email no registrado rechazado correctamente.")


def test_login_empty_fields(driver):
    """TC-LOGIN-04: Login falla con campos vacíos."""
    login = Login(driver)
    login.open(LOGIN_URL)
    login.click_login()

    assert not login.is_logged_in(timeout=3), (
        "❌ No debe loguearse con campos vacíos."
    )
    print("✅ Campos vacíos bloquearon el login correctamente.")


def test_login_empty_password(driver):
    """TC-LOGIN-05: Login falla con contraseña vacía."""
    login = Login(driver)
    login.open(LOGIN_URL)
    login.enter_email(VALID_EMAIL)
    login.click_login()

    assert not login.is_logged_in(timeout=3), (
        "❌ No debe loguearse sin contraseña."
    )
    print("✅ Contraseña vacía bloqueó el login correctamente.")

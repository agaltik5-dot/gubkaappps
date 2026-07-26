import os
import sys
import asyncio
import logging
import sqlite3
import re
import requests
import json
from pathlib import Path
from datetime import datetime, timedelta
from collections import defaultdict
from cachetools import TTLCache
from typing import Dict
from aiogram.utils.keyboard import ReplyKeyboardBuilder, InlineKeyboardBuilder
from dotenv import load_dotenv
from aiogram import Bot, Dispatcher, types
from aiogram.filters import Command, CommandStart
from aiogram.client.default import DefaultBotProperties
from aiogram.fsm.storage.memory import MemoryStorage
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.enums import ParseMode
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup, CallbackQuery
from aiogram.types import ReplyKeyboardRemove
from calendar import monthrange, month_name
from pytz import timezone
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger

# Загрузка переменных окружения
load_dotenv()

# Настройка логирования
logging.basicConfig(
    level=logging.DEBUG if os.getenv('DEBUG') else logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('bot.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)
GROUPS_CACHE_DIR = Path('groups_cache')
GROUPS_CACHE_DIR.mkdir(exist_ok=True)

def get_groups_cache_path(faculty_id: int) -> Path:
    """Возвращает путь к файлу кэша для факультета"""
    return GROUPS_CACHE_DIR / f"faculty_{faculty_id}.json"

def load_groups_from_cache(faculty_id: int):
    """Загружает группы из кэша (бессрочное хранение)"""
    cache_path = get_groups_cache_path(faculty_id)
    if not cache_path.exists():
        return None
    try:
        with cache_path.open('r', encoding='utf-8') as f:
            data = json.load(f)
            # Преобразуем обратно в список кортежей
            if isinstance(data, list):
                return [(item[0], item[1]) for item in data]
        return None
    except Exception as e:
        logger.warning(f"Не удалось прочитать кэш групп для факультета {faculty_id}: {e}")
        return None

def save_groups_to_cache(faculty_id: int, groups: list):
    """Сохраняет группы в кэш"""
    cache_path = get_groups_cache_path(faculty_id)
    try:
        with cache_path.open('w', encoding='utf-8') as f:
            # Сохраняем как список списков для JSON сериализации
            json_data = [[code, id_] for code, id_ in groups]
            json.dump(json_data, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.warning(f"Не удалось сохранить кэш групп для факультета {faculty_id}: {e}")
        return False
# Проверка токена
API_TOKEN = os.getenv('TELEGRAM_BOT_TOKEN')
if not API_TOKEN:
    raise ValueError("Токен бота не найден в файле .env!")

# Администраторы для широковещательных рассылок (через переменную окружения ADMIN_IDS="123,456")
raw_admin_ids = os.getenv('ADMIN_IDS', '').strip()
ADMIN_IDS = set()
if raw_admin_ids:
    try:
        ADMIN_IDS = {int(x) for x in raw_admin_ids.split(',') if x.strip().isdigit()}
    except Exception:
        ADMIN_IDS = set()

# Инициализация бота
bot = Bot(
    token=API_TOKEN,
    default=DefaultBotProperties(parse_mode=ParseMode.HTML)
)
storage = MemoryStorage()
dp = Dispatcher(storage=storage)

MOSCOW_TZ = timezone('Europe/Moscow')
# Кэш для групп с TTL 1 час
GROUPS_CACHE_TTL = 3600
# Кэш для сессии с куки (TTL 30 минут)
SESSION_CACHE_TTL = 1800
session_cache = TTLCache(maxsize=1, ttl=SESSION_CACHE_TTL)


# ===== Улучшенная система обхода блокировок IP =====
import random
import time
import subprocess
import socket
from itertools import cycle

# Формат переменной окружения PROXY_LIST: 
#   http://user:pass@host1:port,https://user:pass@host2:port
RAW_PROXY_LIST = os.getenv('PROXY_LIST', '').strip()
_proxy_list = [p.strip() for p in RAW_PROXY_LIST.split(',') if p.strip()]
_proxy_cycle = cycle(_proxy_list) if _proxy_list else None
_proxy_failures = {}  # Счетчик неудач для каждого прокси

# Резервный прокси URL (используется если основной список прокси пуст)
ALT_PROXY_URL = os.getenv('ALT_PROXY_URL', '').strip()

# Список User-Agent для ротации
USER_AGENTS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15',
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/121.0'
]

#

def _get_next_proxy_url():
    """Получает следующий прокси с учетом неудач (всегда возвращает прокси, никогда None)"""
    if not _proxy_cycle:
        # Если список прокси пуст, используем резервный прокси
        if ALT_PROXY_URL:
            logger.info(f"Используем резервный прокси: {ALT_PROXY_URL}")
            return ALT_PROXY_URL
        else:
            logger.error("Нет доступных прокси! Проверьте настройки PROXY_LIST или ALT_PROXY_URL")
            return None
    
    # Пропускаем прокси с большим количеством неудач
    max_failures = 3
    for _ in range(len(_proxy_list)):
        proxy_url = next(_proxy_cycle)
        if _proxy_failures.get(proxy_url, 0) < max_failures:
            return proxy_url
    
    # Если все прокси имеют много неудач, сбрасываем счетчики
    _proxy_failures.clear()
    return next(_proxy_cycle)

def _mark_proxy_failure(proxy_url):
    """Отмечает неудачу прокси"""
    if proxy_url:
        _proxy_failures[proxy_url] = _proxy_failures.get(proxy_url, 0) + 1
        logger.warning(f"Прокси {proxy_url} отмечен как неудачный (попытка {_proxy_failures[proxy_url]})")
        
        # Если используем Tor и произошла блокировка, обновляем цепь
        if proxy_url == "tor":
            logger.info("Обновляем цепь Tor из-за блокировки")
            renew_tor_circuit()

def _mark_proxy_success(proxy_url):
    """Отмечает успех прокси"""
    if proxy_url and proxy_url in _proxy_failures:
        _proxy_failures[proxy_url] = 0
        logger.info(f"Прокси {proxy_url} восстановлен")

def get_random_user_agent():
    """Возвращает случайный User-Agent"""
    return random.choice(USER_AGENTS)

def add_random_delay():
    """Добавляет случайную задержку между запросами"""
    delay = random.uniform(1.0, 3.0)  # 1-3 секунды
    time.sleep(delay)

# ===== Поддержка Tor =====
TOR_SOCKS_PORT = 9050
TOR_CONTROL_PORT = 9051
TOR_PASSWORD = os.getenv('TOR_PASSWORD', '')

def check_tor_connection():
    """Проверяет доступность Tor"""
    try:
        # Проверяем SOCKS порт
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        result = sock.connect_ex(('127.0.0.1', TOR_SOCKS_PORT))
        sock.close()
        return result == 0
    except Exception:
        return False

def renew_tor_circuit():
    """Обновляет цепь Tor для смены IP"""
    try:
        if not TOR_PASSWORD:
            logger.warning("TOR_PASSWORD не установлен, пропускаем обновление цепи")
            return False
            
        # Используем socket для подключения к Tor Control Port
        import socket
        
        try:
            # Подключаемся к Tor Control Port
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('127.0.0.1', TOR_CONTROL_PORT))
            
            # Читаем приветствие
            response = sock.recv(1024).decode()
            if "250 OK" not in response:
                logger.error("Неожиданный ответ от Tor Control Port")
                return False
            
            # Аутентификация
            auth_cmd = f'AUTHENTICATE "{TOR_PASSWORD}"\n'
            sock.send(auth_cmd.encode())
            response = sock.recv(1024).decode()
            if "250 OK" not in response:
                logger.error("Ошибка аутентификации с Tor")
                return False
            
            # Обновляем цепь
            sock.send(b'signal NEWNYM\n')
            response = sock.recv(1024).decode()
            if "250 OK" not in response:
                logger.error("Ошибка обновления цепи Tor")
                return False
            
            # Закрываем соединение
            sock.send(b'quit\n')
            sock.close()
            
            logger.info("Цепь Tor обновлена")
            return True
            
        except socket.timeout:
            logger.error("Таймаут при подключении к Tor Control Port")
            return False
        except Exception as e:
            logger.error(f"Ошибка подключения к Tor: {e}")
            return False
            
    except Exception as e:
        logger.error(f"Ошибка обновления цепи Tor: {e}")
        return False

def create_tor_session():
    """Создает сессию через Tor"""
    session = requests.Session()
    session.proxies = {
        'http': f'socks5://127.0.0.1:{TOR_SOCKS_PORT}',
        'https': f'socks5://127.0.0.1:{TOR_SOCKS_PORT}'
    }
    session.trust_env = False
    return session

def create_session_with_proxy():
    """Создает requests.Session с использованием ТОЛЬКО прокси (основной IP не используется)"""
    session = requests.Session()
    
    # Настройка таймаутов
    session.timeout = (10, 30)  # (connect, read)
    
    # Проверяем доступность Tor
    use_tor = check_tor_connection()
    proxy_url = None
    
    if use_tor:
        # Используем Tor
        session = create_tor_session()
        logger.info("Используем Tor для подключения")
        proxy_url = "tor"
    else:
        # Используем обычные прокси
        proxy_url = _get_next_proxy_url()
        if proxy_url:
            session.proxies = {
                'http': proxy_url,
                'https': proxy_url,
            }
            session.trust_env = False
            logger.info(f"Используем прокси: {proxy_url}")
        else:
            # Если нет доступных прокси, логируем ошибку
            logger.error("Нет доступных прокси! Проверьте настройки PROXY_LIST или ALT_PROXY_URL")
            # Можно выбросить исключение или вернуть None
            return None, None
    
    # Устанавливаем случайный User-Agent
    session.headers.update({
        'User-Agent': get_random_user_agent()
    })
    
    return session, proxy_url


def get_session_cookies():
    """Получение куки для доступа к API расписания с кэшированием"""
    # Проверяем кэш
    if 'session' in session_cache:
        return session_cache['session']
    
    # Добавляем случайную задержку
    add_random_delay()
    
    # Создаем сессию с улучшенной системой прокси
    session, proxy_url = create_session_with_proxy()
    
    # ДОБАВИТЬ ПРОВЕРКУ:
    if session is None:
        logger.error("Не удалось создать сессию с прокси")
        return None
    
    # Заголовки для получения куки
    cookie_headers = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none'
    }
    
    # Получаем куки с главной страницы расписания
    try:
        logger.info("Получаем сессионные куки...")
        main_page_response = session.get('https://lk.gubkin.ru/schedule/', headers=cookie_headers, timeout=30)
        main_page_response.raise_for_status()
        logger.info("Куки получены успешно")
        
        # Отмечаем успех прокси
        _mark_proxy_success(proxy_url)
        
        # Кэшируем сессию
        session_cache['session'] = session
        return session
    except Exception as e:
        logger.error(f"Ошибка получения куки: {str(e)}")
        # Отмечаем неудачу прокси
        _mark_proxy_failure(proxy_url)
        return None


# ===== Файловый кеш недельного расписания =====
WEEK_CACHE_DIR = Path(os.getenv('WEEK_CACHE_DIR', 'cache'))
WEEK_CACHE_DIR.mkdir(parents=True, exist_ok=True)
WEEK_CACHE_TTL_SECONDS = int(os.getenv('WEEK_CACHE_TTL_SECONDS', '604800'))  # 12 часов по умолчанию


def _week_cache_key(date_obj: datetime) -> str:
    """Ключ недели по ISO (YYYY-Www)."""
    iso_year, iso_week, _ = date_obj.isocalendar()
    return f"{iso_year}-W{iso_week:02d}"


def _week_cache_path(group_id: int, date_obj: datetime) -> Path:
    key = _week_cache_key(date_obj)
    return WEEK_CACHE_DIR / f"week_{group_id}_{key}.json"


def _is_cache_fresh(path: Path) -> bool:
    if not path.exists():
        return False
    try:
        mtime = datetime.fromtimestamp(path.stat().st_mtime)
        return (datetime.now() - mtime).total_seconds() < WEEK_CACHE_TTL_SECONDS
    except Exception:
        return False


def load_week_from_cache(group_id: int, date_obj: datetime):
    path = _week_cache_path(group_id, date_obj)
    if not _is_cache_fresh(path):
        return None
    try:
        with path.open('r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        logger.warning(f"Не удалось прочитать кеш недели {path}: {e}")
        return None


def save_week_to_cache(group_id: int, date_obj: datetime, data: dict):
    path = _week_cache_path(group_id, date_obj)
    try:
        with path.open('w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False)
        return True
    except Exception as e:
        logger.warning(f"Не удалось сохранить кеш недели {path}: {e}")
        return False

def invalidate_week_cache(group_id: int, date_obj: datetime) -> bool:
    """Удаляет файл кеша недели для указанной даты"""
    try:
        path = _week_cache_path(group_id, date_obj)
        if path.exists():
            path.unlink()
        return True
    except Exception as e:
        logger.warning(f"Не удалось удалить кеш недели: {e}")
        return False


def fetch_week_schedule_json(group_id: int, date_obj: datetime):
    """Возвращает JSON расписания недели для группы. Использует файловый кеш."""
    # 1) Пытаемся из кеша
    cached = load_week_from_cache(group_id, date_obj)
    if cached:
        return cached

    # 2) Идем в сеть один раз на неделю и сохраняем
    # Добавляем случайную задержку
    add_random_delay()
    
    session, proxy_url = create_session_with_proxy()
    
    # ДОБАВИТЬ ПРОВЕРКУ:
    if session is None:
        logger.error("Не удалось создать сессию с прокси для получения расписания")
        return None

    # Логируем исходящий IP (через ту же сессию/прокси)
    try:
        ip_resp = session.get('https://api.ipify.org', params={'format': 'text'}, timeout=10)
        if ip_resp.ok:
            logger.info(f"Запрос расписания: исходящий IP={ip_resp.text.strip()} (proxy={proxy_url})")
        else:
            logger.info(f"Запрос расписания: не удалось определить исходящий IP (proxy={proxy_url})")
    except Exception as _e:
        logger.info(f"Запрос расписания: ошибка определения IP (proxy={proxy_url}): {_e}")

    cookie_headers = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none'
    }

    try:
        session.get('https://lk.gubkin.ru/schedule/', headers=cookie_headers, timeout=30)
    except Exception as e:
        logger.error(f"Ошибка получения куки: {e}")
        _mark_proxy_failure(proxy_url)
        return None

    api_headers = {
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Referer': 'https://lk.gubkin.ru/schedule/',
        'Origin': 'https://lk.gubkin.ru',
        'Connection': 'keep-alive',
        'Sec-Fetch-Dest': 'empty',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'same-origin'
    }

    api_date = date_obj.strftime("%d-%m-%Y")  # БЫЛО: "%Y-%m-%d"
    url = f"https://lk.gubkin.ru/schedule/api/api.php?act=schedule&date={api_date}&groupId={group_id}"

    try:
        response = session.get(url, headers=api_headers, timeout=30)
        response.raise_for_status()
        data = response.json()
        
        # Отмечаем успех прокси
        _mark_proxy_success(proxy_url)
        
        # Кладем в кеш, только если формат корректный
        if isinstance(data, dict) and data.get('state'):
            save_week_to_cache(group_id, date_obj, data)
        return data
    except Exception as e:
        logger.error(f"Ошибка запроса недели: {e}")
        _mark_proxy_failure(proxy_url)
        return None

# Состояния FSM
class Form(StatesGroup):
    waiting_date_calendar = State()
    waiting_faculty = State()
    waiting_group_input = State()
    waiting_date = State()
    waiting_notification_time = State()

# Хранилища данных
user_data = {}  # Для ID групп
class Database:
    def __init__(self):
        self.conn = sqlite3.connect('users.db')
        self.cursor = self.conn.cursor()
        self._create_tables()

    def _create_tables(self):
        """Создание таблиц, если они не существуют"""
        # Таблица пользователей
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY,
                group_id INTEGER
            )
        ''')
        
        # Таблица настроек пользователей
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS user_settings (
                user_id INTEGER PRIMARY KEY,
                notifications_enabled BOOLEAN DEFAULT FALSE,
                notification_time TEXT DEFAULT '08:00',
                last_manual_refresh TEXT,
                FOREIGN KEY(user_id) REFERENCES users(user_id)
            )
        ''')
        
        # Флаги системы (глобальные переключатели)
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS system_flags (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        ''')
        
        self.conn.commit()
        
        # ДОБАВЛЯЕМ НОВЫЕ КОЛОНКИ ДЛЯ НАСТРОЕК ОТОБРАЖЕНИЯ
        self._add_missing_columns()

    def _add_missing_columns(self):
        """Добавляет недостающие колонки в таблицу user_settings"""
        try:
            # Получаем список существующих колонок
            self.cursor.execute("PRAGMA table_info(user_settings)")
            existing_columns = [row[1] for row in self.cursor.fetchall()]
            
            # Колонки, которые нужно добавить
            columns_to_add = [
                ('teacher_name_format', 'TEXT DEFAULT "short"'),
                ('show_consultations', 'BOOLEAN DEFAULT TRUE'),
                ('show_rooms', 'BOOLEAN DEFAULT TRUE'),
                ('compact_mode', 'BOOLEAN DEFAULT FALSE')
            ]
            
            for column_name, column_type in columns_to_add:
                if column_name not in existing_columns:
                    logger.info(f"Добавляем колонку {column_name} в user_settings")
                    self.cursor.execute(f'''
                        ALTER TABLE user_settings 
                        ADD COLUMN {column_name} {column_type}
                    ''')
            
            self.conn.commit()
            logger.info("Проверка структуры таблицы user_settings завершена")
            
        except Exception as e:
            logger.error(f"Ошибка при добавлении колонок: {e}")
            # В случае ошибки пробуем создать таблицу заново
            try:
                self.cursor.execute('DROP TABLE IF EXISTS user_settings_backup')
                self.cursor.execute('''
                    CREATE TABLE user_settings_backup AS 
                    SELECT * FROM user_settings
                ''')
                self.cursor.execute('DROP TABLE IF EXISTS user_settings')
                self.cursor.execute('''
                    CREATE TABLE IF NOT EXISTS user_settings (
                        user_id INTEGER PRIMARY KEY,
                        notifications_enabled BOOLEAN DEFAULT FALSE,
                        notification_time TEXT DEFAULT '08:00',
                        last_manual_refresh TEXT,
                        teacher_name_format TEXT DEFAULT 'short',
                        show_consultations BOOLEAN DEFAULT TRUE,
                        show_rooms BOOLEAN DEFAULT TRUE,
                        compact_mode BOOLEAN DEFAULT FALSE,
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    )
                ''')
                self.cursor.execute('''
                    INSERT INTO user_settings 
                    SELECT 
                        user_id,
                        notifications_enabled,
                        notification_time,
                        last_manual_refresh,
                        'short' as teacher_name_format,
                        TRUE as show_consultations,
                        TRUE as show_rooms,
                        FALSE as compact_mode
                    FROM user_settings_backup
                ''')
                self.cursor.execute('DROP TABLE IF EXISTS user_settings_backup')
                self.conn.commit()
                logger.info("Таблица user_settings пересоздана с новой структурой")
            except Exception as e2:
                logger.error(f"Критическая ошибка при пересоздании таблицы: {e2}")

    def enable_notifications(self, user_id: int):
        self.cursor.execute('''
            INSERT OR REPLACE INTO user_settings (user_id, notifications_enabled)
            VALUES (?, TRUE)
        ''', (user_id,))
        self.conn.commit()

    def disable_notifications(self, user_id: int):
        self.cursor.execute('''
            INSERT OR REPLACE INTO user_settings (user_id, notifications_enabled)
            VALUES (?, FALSE)
        ''', (user_id,))
        self.conn.commit()

    def get_notifications_status(self, user_id: int) -> bool:
        self.cursor.execute('''
            SELECT notifications_enabled FROM user_settings WHERE user_id = ?
        ''', (user_id,))
        result = self.cursor.fetchone()
        return result[0] if result else False
        
    def update_group(self, user_id: int, group_id: int):
        """Обновление группы пользователя с улучшенной обработкой ошибок"""
        try:
            logger.info(f"Попытка обновления группы для user_id={user_id}, group_id={group_id}")
            
            # Проверяем корректность данных
            if not isinstance(user_id, int) or user_id <= 0:
                logger.error(f"Некорректный user_id: {user_id}")
                return False
                
            if not isinstance(group_id, int) or group_id <= 0:
                logger.error(f"Некорректный group_id: {group_id}")
                return False

            # Проверяем существование пользователя
            self.cursor.execute('SELECT 1 FROM users WHERE user_id = ?', (user_id,))
            exists = self.cursor.fetchone()

            if exists:
                # Обновляем существующую запись
                self.cursor.execute('''
                    UPDATE users 
                    SET group_id = ?
                    WHERE user_id = ?
                ''', (group_id, user_id))
                logger.info(f"Обновлена существующая запись для user_id={user_id}")
            else:
                # Создаем новую запись
                self.cursor.execute('''
                    INSERT INTO users (user_id, group_id)
                    VALUES (?, ?)
                ''', (user_id, group_id))
                logger.info(f"Создана новая запись для user_id={user_id}")

            # Явно коммитим изменения
            self.conn.commit()
            
            # Проверяем, что запись действительно сохранилась
            self.cursor.execute('SELECT group_id FROM users WHERE user_id = ?', (user_id,))
            verify = self.cursor.fetchone()
            
            if verify and verify[0] == group_id:
                logger.info(f"✅ Успешно сохранено: user_id={user_id}, group_id={group_id}")
                return True
            else:
                logger.error(f"❌ Ошибка верификации записи для user_id={user_id}")
                return False
            
        except sqlite3.Error as e:
            logger.error(f"❌ Ошибка SQLite при обновлении группы: {e}")
            self.conn.rollback()
            return False
        except Exception as e:
            logger.error(f"❌ Неожиданная ошибка при обновлении группы: {e}")
            self.conn.rollback()
            return False

    def get_user(self, user_id: int) -> int:
        self.cursor.execute('SELECT group_id FROM users WHERE user_id = ?', (user_id,))
        data = self.cursor.fetchone()
        return data[0] if data else None

    def get_all_user_ids(self):
        """Возвращает список всех user_id, когда-либо сохранявшихся в боте"""
        self.cursor.execute('SELECT user_id FROM users')
        return [row[0] for row in self.cursor.fetchall()]

    def get_users_with_notifications(self):
        self.cursor.execute('''
            SELECT user_id FROM user_settings 
            WHERE notifications_enabled = TRUE
        ''')
        return [row[0] for row in self.cursor.fetchall()]


    def set_notification_time(self, user_id: int, time: str):
        self.cursor.execute('''
            INSERT OR REPLACE INTO user_settings 
            (user_id, notifications_enabled, notification_time)
            VALUES (?, COALESCE((SELECT notifications_enabled FROM user_settings WHERE user_id = ?), FALSE), ?)
        ''', (user_id, user_id, time))
        self.conn.commit()

    def get_notification_time(self, user_id: int) -> str:
        self.cursor.execute('SELECT notification_time FROM user_settings WHERE user_id = ?', (user_id,))
        result = self.cursor.fetchone()
        return result[0] if result else '08:00'

    def get_last_manual_refresh(self, user_id: int):
        """Возвращает datetime последнего ручного обновления или None"""
        try:
            self.cursor.execute('SELECT last_manual_refresh FROM user_settings WHERE user_id = ?', (user_id,))
            row = self.cursor.fetchone()
            if row and row[0]:
                return datetime.fromisoformat(row[0])
        except Exception:
            pass
        return None

    def set_last_manual_refresh(self, user_id: int, dt: datetime):
        """Сохраняет время последнего ручного обновления"""
        iso = dt.isoformat()
        self.cursor.execute('''
            INSERT OR REPLACE INTO user_settings 
            (user_id, notifications_enabled, notification_time, last_manual_refresh)
            VALUES (
                ?,
                COALESCE((SELECT notifications_enabled FROM user_settings WHERE user_id = ?), FALSE),
                COALESCE((SELECT notification_time FROM user_settings WHERE user_id = ?), '08:00'),
                ?
            )
        ''', (user_id, user_id, user_id, iso))
        self.conn.commit()
    def get_display_settings(self, user_id: int) -> dict:
        """Получает настройки отображения для пользователя"""
        try:
            self.cursor.execute('''
                SELECT teacher_name_format, show_consultations, show_rooms, compact_mode 
                FROM user_settings WHERE user_id = ?
            ''', (user_id,))
            result = self.cursor.fetchone()
            if result:
                return {
                    'teacher_name_format': result[0] or 'short',
                    'show_consultations': bool(result[1]) if result[1] is not None else True,
                    'show_rooms': bool(result[2]) if result[2] is not None else True,
                    'compact_mode': bool(result[3]) if result[3] is not None else False
                }
        except sqlite3.OperationalError as e:
            if "no such column" in str(e):
                logger.warning("Колонки настроек отображения не найдены, используем значения по умолчанию")
                # Вызываем обновление структуры
                self._add_missing_columns()
            else:
                logger.error(f"Ошибка базы данных: {e}")
        
        # Возвращаем значения по умолчанию
        return {
            'teacher_name_format': 'short',
            'show_consultations': True,
            'show_rooms': True,
            'compact_mode': False
        }

    def set_display_setting(self, user_id: int, setting_name: str, value):
        """Устанавливает настройку отображения"""
        # Сначала убедимся, что запись существует
        self.cursor.execute('''
            INSERT OR IGNORE INTO user_settings 
            (user_id, notifications_enabled, notification_time, teacher_name_format, show_consultations, show_rooms, compact_mode)
            VALUES (?, FALSE, '08:00', 'short', TRUE, TRUE, FALSE)
        ''', (user_id,))
        
        # Обновляем конкретную настройку
        self.cursor.execute(f'''
            UPDATE user_settings SET {setting_name} = ? WHERE user_id = ?
        ''', (value, user_id))
        self.conn.commit()

    def _create_tables(self):
        """Создание таблиц, если они не существуют"""
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY,
                group_id INTEGER
            )
        ''')
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS user_settings (
                user_id INTEGER PRIMARY KEY,
                notifications_enabled BOOLEAN DEFAULT FALSE,
                notification_time TEXT DEFAULT '08:00',
                last_manual_refresh TEXT,
                teacher_name_format TEXT DEFAULT 'short',
                show_consultations BOOLEAN DEFAULT TRUE,
                show_rooms BOOLEAN DEFAULT TRUE,
                compact_mode BOOLEAN DEFAULT FALSE,
                FOREIGN KEY(user_id) REFERENCES users(user_id)
            )
        ''')
    def get_flag(self, key: str):
        self.cursor.execute('SELECT value FROM system_flags WHERE key = ?', (key,))
        row = self.cursor.fetchone()
        return row[0] if row else None

    def set_flag(self, key: str, value: str):
        self.cursor.execute('''
            INSERT INTO system_flags(key, value)
            VALUES(?, ?)
            ON CONFLICT(key) DO UPDATE SET value=excluded.value
        ''', (key, value))
        self.conn.commit()

# Инициализация базы данных
db = Database()
# Список факультетов
FACULTIES = {
    0: "ФГиГНиГ",
    1: "ФРНиГМ",
    2: "ФПСиЭСТТ",
    3: "ФИМ",
    4: "ФХТиЭ",
    5: "АиВТ",
    6: "ФЭиУ",
    12: "ФМЭБ",
    21: "ФКБ ТЭК"
}




def fetch_groups(faculty_id: int):
    """Получение списка групп с бессрочным файловым кэшированием"""
    # 1) Проверяем файловый кэш (бессрочный)
    cached_groups = load_groups_from_cache(faculty_id)
    if cached_groups:
        logger.info(f"Группы для факультета {faculty_id} загружены из кэша")
        return cached_groups

    # 2) Если нет в кэше, загружаем с сервера только один раз
    logger.info(f"Загружаем группы для факультета {faculty_id} с сервера...")
    
    # Добавляем случайную задержку
    add_random_delay()

    # Сначала получаем куки с главной страницы
    session, proxy_url = create_session_with_proxy()
    
    if session is None:
        logger.error("Не удалось создать сессию с прокси для получения групп")
        return []

    # Заголовки для имитации браузера
    headers = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none'
    }
    
    # Получаем куки с главной страницы расписания
    try:
        logger.info("Получаем сессионные куки для групп...")
        main_page_response = session.get('https://lk.gubkin.ru/schedule/', headers=headers, timeout=30)
        main_page_response.raise_for_status()
        logger.info("Куки получены успешно")
    except Exception as e:
        logger.error(f"Ошибка получения куки: {str(e)}")
        _mark_proxy_failure(proxy_url)
        return []

    url = f"https://lk.gubkin.ru/schedule/api/api.php?act=list&method=getFacultyGroups&facultyId={faculty_id}"
    
    # Обновляем заголовки для API запроса
    api_headers = {
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Referer': 'https://lk.gubkin.ru/schedule/',
        'Origin': 'https://lk.gubkin.ru',
        'Connection': 'keep-alive',
        'Sec-Fetch-Dest': 'empty',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'same-origin'
    }
    
    try:
        response = session.get(url, headers=api_headers, timeout=30)
        response.raise_for_status()
        data = response.json()
        if data.get('state') and isinstance(data.get('rows'), list):
            groups = [(group['code'], group['id']) for group in data['rows']]
            
            # Сохраняем в файловый кэш навсегда
            save_groups_to_cache(faculty_id, groups)
            
            # Отмечаем успех прокси
            _mark_proxy_success(proxy_url)
            
            logger.info(f"Группы для факультета {faculty_id} загружены с сервера и сохранены в кэш ({len(groups)} групп)")
            return groups
        logger.warning(f"Неверный формат данных для факультета {faculty_id}")
        return []
    except Exception as e:
        logger.error(f"Ошибка получения групп: {str(e)}")
        _mark_proxy_failure(proxy_url)
        return []


def group_years(groups):
    """Группировка по годам обучения"""
    grouped = defaultdict(list)
    for code, id_ in groups:
        try:
            year_part = code.split('-')[1]
            year = int(year_part[:2])
            study_year = datetime.now().year - (2000 + year) + 1
            grouped[study_year].append((code, id_))
        except (IndexError, ValueError, TypeError):
            logger.warning(f"Не удалось определить год для группы: {code}")
    return grouped


async def get_schedule(user_id: int, date: str) -> tuple:
    """Возвращает кортеж (текст_расписания, клавиатура_для_возврата)"""
    try:
        group_id = db.get_user(user_id)
        if not group_id:
            return "❌ Сначала установите группу через /set_group", main_keyboard(user_id)

        # Получаем настройки отображения
        display_settings = db.get_display_settings(user_id)

        try:
            date_obj = datetime.strptime(date, "%d-%m-%Y")
            api_date = date_obj.strftime("%Y-%m-%d")
            target_date = date_obj.strftime("%d-%m-%Y")
            weekday = date_obj.strftime("%A")
        except ValueError:
            return "❌ Неверный формат даты! Используйте ДД-ММ-ГГГГ", main_keyboard(user_id)

        # Преобразуем русские дни недели
        weekdays_ru = {
            'Monday': 'Понедельник',
            'Tuesday': 'Вторник', 
            'Wednesday': 'Среда',
            'Thursday': 'Четверг',
            'Friday': 'Пятница',
            'Saturday': 'Суббота',
            'Sunday': 'Воскресенье'
        }
        weekday_ru = weekdays_ru.get(weekday, weekday)

        # Создаем клавиатуру заранее
        def create_schedule_keyboard(target_date: str):
            """Создает стандартную клавиатуру для расписания"""
            back_keyboard = InlineKeyboardBuilder()
            back_keyboard.row(
                InlineKeyboardButton(text="📅 Выбрать другую дату", callback_data="back_to_calendar"),
                width=2
            )
            back_keyboard.row(
                InlineKeyboardButton(text="◀", callback_data=f"nav_day_prev_{target_date}"),
                InlineKeyboardButton(text="▶", callback_data=f"nav_day_next_{target_date}"),
                width=2
            )
            back_keyboard.row(
                InlineKeyboardButton(text="🏠 Главное меню", callback_data="back_to_main"),
                width=2
            )
            return back_keyboard.as_markup()

        # Получаем данные расписания
        data = fetch_week_schedule_json(group_id, date_obj)
        if not data:
            keyboard = create_schedule_keyboard(target_date)
            return "❌ Проблема с подключением к серверу", keyboard

        if not data.get('state') or not isinstance(data.get('rows'), dict):
            keyboard = create_schedule_keyboard(target_date)
            return "❌ Ошибка формата данных сервера", keyboard

        moscow_org = next(
            (org for org in data['rows'].get('organizations', [])
             if isinstance(org, dict) and org.get('name') == 'Москва'),
            None
        )

        if not moscow_org:
            keyboard = create_schedule_keyboard(target_date)
            return "❌ Расписание для Москвы недоступно", keyboard

        week_data = data['rows'].get('week', {}).get('weekRussia', {})
        target_day = next(
            (day for day in week_data.get('days', [])
             if day.get('date') == target_date and day.get('isStudyDay')),
            None
        )

        # ВАЖНОЕ ИЗМЕНЕНИЕ: даже если день не учебный, показываем стандартный интерфейс
        if not target_day:
            keyboard = create_schedule_keyboard(target_date)
            header = (
                f"<b>📅 {date}</b>\n"
                f"<i>{weekday_ru}</i>\n\n"
            )
            return header + "🎉 Отличные новости! Занятий нет!", keyboard

        target_weekday = target_day.get('weekDayNumber')
        
        # Фильтруем занятия по настройкам
        lessons = []
        for lesson in moscow_org.get('lessons', []):
            if not isinstance(lesson, dict):
                continue
            if lesson.get('weekDayNumber') != target_weekday:
                continue
            if not any(group.get('id') == group_id for group in lesson.get('groups', [])):
                continue
                
            # УЛУЧШЕННАЯ ФИЛЬТРАЦИЯ КОНСУЛЬТАЦИЙ
            if not display_settings['show_consultations']:
                lesson_type = lesson.get('type', '').lower()
                subject_name = lesson.get('course', {}).get('name', '').lower()
                
                # Проверяем различные варианты обозначения консультаций
                consultation_keywords = [
                    'консультация', 'консульт', 'consultation', 
                    'мероприятие', 'event', 'собрание', 'meeting'
                ]
                
                is_consultation = any(
                    keyword in lesson_type or keyword in subject_name 
                    for keyword in consultation_keywords
                )
                
                # Дополнительная проверка: если в названии предмете есть "консультация"
                # или тип занятия указывает на мероприятие/консультацию
                if (is_consultation or 
                    'консультация' in subject_name or
                    lesson_type in ['мероприятие', 'event', 'собрание']):
                    continue
                    
            lessons.append(lesson)

        # Сортировка занятий по времени начала
        time_chunks = moscow_org.get('lessonsTimeChunks', [])

        def time_to_minutes(t):
            try:
                hours, mins = map(int, t.split(':'))
                return hours * 60 + mins
            except:
                return 0

        if time_chunks:
            lessons.sort(key=lambda lesson: (
                time_to_minutes(
                    time_chunks[lesson['timeChunks'][0]].split('-')[0].strip()
                ) if lesson.get('timeChunks')
                     and len(lesson['timeChunks']) > 0
                     and lesson['timeChunks'][0] < len(time_chunks)
                else 0,
                lesson.get('subgroup', 0)
            ))

        # Группируем занятия по времени
        grouped_lessons = {}
        for lesson in lessons:
            try:
                time_indices = lesson.get('timeChunks', [])
                lesson_time = (
                    f"{time_chunks[time_indices[0]].split('-')[0].strip()}-"
                    f"{time_chunks[time_indices[-1]].split('-')[1].strip()}"
                    if time_indices and time_chunks
                    else "⏰ —"
                )
                
                key = lesson_time
                if key not in grouped_lessons:
                    grouped_lessons[key] = []
                grouped_lessons[key].append(lesson)
                
            except Exception as e:
                logger.error(f"Ошибка группировки занятия: {str(e)}", exc_info=True)
                continue

        schedule = []
        lesson_counter = 1
        
        for key, lesson_group in grouped_lessons.items():
            lesson_group.sort(key=lambda x: (x.get('subgroup', 0), x.get('course', {}).get('name', '')))
            
            first_lesson = lesson_group[0]
            time_indices = first_lesson.get('timeChunks', [])
            lesson_time = (
                f"{time_chunks[time_indices[0]].split('-')[0].strip()}-"
                f"{time_chunks[time_indices[-1]].split('-')[1].strip()}"
                if time_indices and time_chunks
                else "⏰ —"
            )
            
            try:
                subjects_info = []
                
                for lesson in lesson_group:
                    # Инициализируем все переменные в начале
                    rooms_list = []
                    teachers_list = []
                    move_details = []
                    is_moved_to_current = False
                    is_moved_from_current = False
                    move_info = ""
                    
                    # Проверяем статусы переноса и отмены
                    is_canceled = lesson.get('isCanceled', False)
                    is_moved = lesson.get('isMoved', False)
                    
                    subject = lesson.get('course', {}).get('name', 'Без названия')
                    lesson_type = lesson.get('type', '—')
                    subgroup = lesson.get('subgroup', 0)
                    
                    # Обработка изменений (changes) - ПРОСТО ИСПОЛЬЗУЕМ ДАННЫЕ ИЗ changes КАК ОСНОВНЫЕ
                    changes = lesson.get('changes', {})
                    
                    # ПРЕПОДАВАТЕЛИ: сначала проверяем изменения, потом оригинальные данные
                    teachers = []
                    if changes and isinstance(changes, dict) and changes.get('teachers'):
                        teachers = changes.get('teachers', [])
                    else:
                        teachers = lesson.get('teachers', [])
                    
                    # АУДИТОРИИ: сначала проверяем изменения, потом оригинальные данные
                    rooms_to_show = []
                    if changes and isinstance(changes, dict) and changes.get('rooms'):
                        rooms_to_show = changes.get('rooms', [])
                    else:
                        rooms_to_show = lesson.get('rooms', [])
                    
                    # Форматирование ФИО преподавателя (используем данные, которые уже выбраны выше)
                    for teacher in teachers:
                        if isinstance(teacher, dict):
                            last = (teacher.get('lastName') or '').strip()
                            first = (teacher.get('firstName') or '').strip()
                            patron = (teacher.get('patronymic') or '').strip()
                            
                            if last:
                                if display_settings['teacher_name_format'] == 'full':
                                    # Полное ФИО
                                    teacher_name = f"{last} {first} {patron}".strip()
                                else:
                                    # Краткое ФИО (по умолчанию)
                                    parts = []
                                    if first:
                                        parts.append(f"{first[0]}." if first else "")
                                    if patron:
                                        parts.append(f"{patron[0]}." if patron else "")
                                    teacher_name = f"{last} {' '.join(parts)}".strip()
                                teachers_list.append(teacher_name)

                    # Форматирование аудиторий (используем данные, которые уже выбраны выше)
                    if display_settings['show_rooms']:
                        for room in rooms_to_show:
                            if isinstance(room, dict):
                                room_num = str(room.get('number', '—'))
                                if room_num != '—':
                                    rooms_list.append(room_num)

                    # Обработка перенесенных занятий (это оставляем, т.к. это не замена, а перенос)
                    moved_to = lesson.get('movedTo')
                    moved_start_time = lesson.get('movedStartTimeChunkId')
                    moved_from = lesson.get('movedFrom')
                    moved_from_start_time = lesson.get('movedFromStartTimeChunkId')

                    # Если пара была перенесена НА текущую дату (moved_to == текущая дата)
                    if moved_to and moved_to == target_date:
                        is_moved_to_current = True
                        try:
                            # Информация о том, откуда перенесена пара
                            if moved_from:
                                moved_from_date = datetime.strptime(moved_from, "%d-%m-%Y")
                                moved_from_date_str = moved_from_date.strftime("%d.%m.%Y")
                                
                                if moved_from_start_time is not None and moved_from_start_time < len(time_chunks):
                                    moved_from_time = time_chunks[moved_from_start_time].split('-')[0].strip()
                                    move_details.append(f"🔄 Перенесена с {moved_from_date_str} {moved_from_time}")
                                else:
                                    move_details.append(f"🔄 Перенесена с {moved_from_date_str}")
                            else:
                                move_details.append("🔄 Перенесена с другой даты")
                        except Exception as e:
                            move_details.append("🔄 Перенесена с другой даты")

                    # Если пара была перенесена С текущей даты (isMoved = True)
                    elif is_moved and moved_to:
                        is_moved_from_current = True
                        try:
                            moved_date = datetime.strptime(moved_to, "%d-%m-%Y")
                            moved_date_str = moved_date.strftime("%d.%m.%Y")
                            
                            if moved_start_time is not None and moved_start_time < len(time_chunks):
                                moved_time = time_chunks[moved_start_time].split('-')[0].strip()
                                move_details.append(f"🔄 Перенесена на: {moved_date_str} {moved_time}")
                            else:
                                move_details.append(f"🔄 Перенесена на: {moved_date_str}")
                        except Exception as e:
                            move_details.append("🔄 Перенесена")

                    if move_details:
                        move_info = "\n" + "\n".join(move_details)
                    
                    if is_canceled:
                        if display_settings['compact_mode']:
                            subject_info = f"❌ ОТМЕНЕНО: {subject}"
                            if lesson_type != '—':
                                subject_info += f" | {lesson_type}"
                        else:
                            subject_info = (
                                f"──────❌ОТМЕНЕНО──────\n"
                                f"📚 {subject}\n"
                                f"🏷 {lesson_type}\n"
                                f"───────────────────────"
                            )
                        subjects_info.append(subject_info)
                        continue

                    # Формируем информацию о предмете
                    subgroup_info = ""
                    if subgroup > 0:
                        subgroup_info = f" (подгруппа {subgroup})"
                    
                    if display_settings['compact_mode']:
                        # Компактный режим - все в одной строке
                        if is_moved_to_current:
                            subject_info = f"🔄 ПЕРЕНОС: {subject}"
                        else:
                            subject_info = f"📚 {subject}"
                            
                        if lesson_type != '—':
                            subject_info += f" | {lesson_type}{subgroup_info}"
                        if rooms_list:
                            subject_info += f" | 🏫 {', '.join(rooms_list)}"
                        if teachers_list:
                            subject_info += f" | 👤 {', '.join(teachers_list)}"
                        
                        # Добавляем информацию о переносе
                        if move_info:
                            subject_info += move_info.replace('\n', ' | ')
                    else:
                        # ПОЛНЫЙ РЕЖИМ - добавляем явную надпись только для перенесенных пар
                        if is_moved_to_current:
                            subject_info = f"══════  ПЕРЕНОС ПАРЫ ══════\n\n"
                        else:
                            subject_info = ""
                        
                        subject_info += f"📚 {subject}\n"
                        
                        # Собираем тип занятия и аудиторию в одну строку
                        type_and_rooms_parts = []
                        
                        if lesson_type != '—':
                            type_and_rooms_parts.append(f"🏷 {lesson_type}{subgroup_info}")
                        
                        if rooms_list:
                            type_and_rooms_parts.append(f"🏫 {', '.join(rooms_list)}")
                        
                        # Объединяем тип и аудиторию через разделитель
                        if type_and_rooms_parts:
                            subject_info += " • ".join(type_and_rooms_parts) + "\n"
                        
                        # Преподаватель на отдельной строке
                        if teachers_list:
                            subject_info += f"👤 {', '.join(teachers_list)}"
                        
                        # Добавляем информацию о переносе
                        if move_info:
                            subject_info += move_info
                        
                        # Убираем лишний перенос в конце, если преподавателя нет
                        if not teachers_list and not move_info and subject_info.endswith('\n'):
                            subject_info = subject_info[:-1]

                    subjects_info.append(subject_info)

                # Объединяем все предметы
                if display_settings['compact_mode']:
                    separator = " | "
                else:
                    separator = "\n\n"  # Разделитель между разными занятиями в одном временном слоте
                    
                all_subjects = separator.join(subjects_info)

                # Форматирование блока пары
                if display_settings['compact_mode']:
                    schedule.append(f"{lesson_counter}. 🕒 {lesson_time} | {all_subjects}")
                else:
                    schedule.append(
                        f"──────── {lesson_counter} ────────\n"
                        f"🕒 {lesson_time}\n\n"
                        f"{all_subjects}\n"
                    )
                
                lesson_counter += 1

            except Exception as e:
                logger.error(f"Ошибка обработки группы занятий: {str(e)}", exc_info=True)
                continue

        # Создаем клавиатуру (теперь всегда создаем, даже если нет занятий)
        keyboard = create_schedule_keyboard(target_date)

        if schedule:
            header = (
                f"<b>📅 {date}</b>\n"
                f"<i>{weekday_ru}</i>\n\n"
            )
            
            footer = f"\n<b>Всего пар: {len(schedule)}</b>"
            
            return header + "\n".join(schedule) + footer, keyboard
        else:
            # Если день учебный, но занятий нет (например, каникулы)
            header = (
                f"<b>📅 {date}</b>\n"
                f"<i>{weekday_ru}</i>\n\n"
            )
            return header + "🎉 Отличные новости! Занятий нет!", keyboard

    except Exception as e:
        logger.error(f"Критическая ошибка в get_schedule: {str(e)}", exc_info=True)
        # Создаем базовую клавиатуру даже при ошибке
        try:
            keyboard = create_schedule_keyboard(date)
        except:
            keyboard = main_keyboard(user_id)
        return "❌ Произошла критическая ошибка при получении расписания", keyboard




scheduler = AsyncIOScheduler()


async def morning_notifications():
    logger.info("Starting morning notifications...")
    for user_id in db.get_users_with_notifications():
        await send_daily_schedule(user_id)


async def send_daily_schedule(user_id: int):
    try:
        today = datetime.now(MOSCOW_TZ).strftime("%d-%m-%Y")
        schedule_text, _ = await get_schedule(user_id, today)

        if "не найдено" in schedule_text or "недоступно" in schedule_text:
            return

        await bot.send_message(
            user_id,
            f"📅 Ваше расписание на сегодня:\n\n{schedule_text}"
        )
        logger.info(f"Sent morning notification to {user_id}")

    except Exception as e:
        logger.error(f"Error sending morning notification to {user_id}: {str(e)}")

async def schedule_daily_notifications():
    """Планирует уведомления на весь день для всех пользователей"""
    global notification_scheduler
    
    logger.info("Планируем уведомления на день...")
    
    # Очищаем старые уведомления
    if notification_scheduler:
        notification_scheduler.remove_all_jobs()
    
    for user_id in db.get_users_with_notifications():
        await plan_user_notifications(user_id)


async def plan_user_notifications(user_id: int):
    """Планирует уведомления для конкретного пользователя на сегодня"""
    try:
        today = datetime.now(MOSCOW_TZ).strftime("%d-%m-%Y")
        logger.info(f"Планируем уведомления для пользователя {user_id} на {today}")
        
        schedule_text, _ = await get_schedule(user_id, today)

        # Проверяем, есть ли расписание на сегодня
        if "не найдено" in schedule_text or "недоступно" in schedule_text:
            logger.info(f"Нет расписания для пользователя {user_id} на сегодня")
            return

        # Парсим расписание
        lessons = parse_schedule(schedule_text)
        logger.info(f"Парсинг расписания: найдено {len(lessons)} пар")
        
        if not lessons:
            logger.info(f"Нет пар для пользователя {user_id} на сегодня")
            return

        current_time = datetime.now(MOSCOW_TZ)
        logger.info(f"Текущее время: {current_time.strftime('%H:%M')}")
        
        planned_count = 0
        
        # Планируем уведомления для каждой пары
        for i, lesson in enumerate(lessons):
            # Уведомление за 10 минут до окончания пары
            notification_time = lesson['end'] - timedelta(minutes=10)
            
            logger.info(f"Пара {i+1}: {lesson['start'].strftime('%H:%M')}-{lesson['end'].strftime('%H:%M')}, уведомление в {notification_time.strftime('%H:%M')}")
            
            # Проверяем, что время уведомления еще не прошло
            if notification_time > current_time:
                next_lesson = lessons[i + 1] if i + 1 < len(lessons) else None
                await schedule_lesson_notification(user_id, lesson, next_lesson, notification_time)
                logger.info(f"✅ Запланировано уведомление для {user_id} на {notification_time.strftime('%H:%M')}")
                planned_count += 1
            else:
                logger.info(f"⏰ Время уведомления уже прошло для пары в {lesson['start'].strftime('%H:%M')}")
                
        logger.info(f"Итого запланировано уведомлений: {planned_count} из {len(lessons)}")
                
    except Exception as e:
        logger.error(f"Ошибка планирования уведомлений для {user_id}: {str(e)}", exc_info=True)


# Глобальный планировщик для уведомлений
notification_scheduler = None

async def schedule_lesson_notification(user_id: int, current_lesson: dict, next_lesson: dict, notification_time: datetime):
    """Планирует конкретное уведомление"""
    global notification_scheduler
    
    try:
        # Инициализируем планировщик если нужно
        if notification_scheduler is None:
            notification_scheduler = AsyncIOScheduler(timezone=MOSCOW_TZ)
            if not notification_scheduler.running:
                notification_scheduler.start()
        
        # Убедимся, что notification_time имеет временную зону
        if notification_time.tzinfo is None:
            notification_time = MOSCOW_TZ.localize(notification_time)
        
        job_id = f"lesson_notif_{user_id}_{current_lesson['start'].strftime('%H%M')}"
        
        # Удаляем старую задачу если существует
        try:
            notification_scheduler.remove_job(job_id)
        except Exception:
            pass
        
        # Планируем уведомление
        notification_scheduler.add_job(
            send_lesson_notification,
            trigger=DateTrigger(run_date=notification_time),
            args=[user_id, current_lesson, next_lesson],
            id=job_id,
            misfire_grace_time=300  # 5 минут grace period
        )
        
        logger.info(f"Уведомление запланировано для пользователя {user_id} на {notification_time.strftime('%H:%M')}")
        
    except Exception as e:
        logger.error(f"Ошибка планирования уведомления: {str(e)}", exc_info=True)


async def send_lesson_notification(user_id: int, current_lesson: dict, next_lesson: dict):
    """Отправляет уведомление о смене пар"""
    try:
        # Форматируем время окончания текущей пары
        end_time_str = current_lesson['end'].strftime('%H:%M')
        
        if next_lesson:
            # Извлекаем основную информацию из следующей пары
            next_lines = next_lesson['text'].split('\n')
            next_subject = next_lines[0] if len(next_lines) > 0 else "Неизвестно"
            next_room = next_lines[2] if len(next_lines) > 2 else "Аудитория не указана"
            next_teacher = next_lines[3] if len(next_lines) > 3 else "Преподаватель не указан"
            
            notification_text = (
                f"⏰ <b>Скоро следующая пара!</b>\n\n"
                f"🕒 Текущая пара заканчивается в {end_time_str}\n\n"
                f"➡️ <b>Следующая пара:</b>\n"
                f"{next_subject}\n"
                f"{next_room}\n"
                f"{next_teacher}"
            )
        else:
            notification_text = (
                f"⏰ <b>Пара заканчивается</b>\n\n"
                f"🕒 Текущая пара заканчивается в {end_time_str}\n"
                f"🎉 <b>Это последняя пара на сегодня!</b>"
            )

        await bot.send_message(user_id, notification_text, parse_mode=ParseMode.HTML)
        logger.info(f"Отправлено уведомление пользователю {user_id}")
        
    except Exception as e:
        logger.error(f"Ошибка отправки уведомления {user_id}: {str(e)}")

def ensure_timezone(dt: datetime) -> datetime:
    """Убеждается, что datetime имеет временную зону"""
    if dt.tzinfo is None:
        return MOSCOW_TZ.localize(dt)
    return dt


def parse_schedule(schedule_text: str) -> list:
    """Парсит расписание и возвращает список пар с временем начала и окончания"""
    lessons = []
    
    try:
        # Исправленное регулярное выражение - ищем время в формате "8:30-10:00" (без ведущих нулей)
        time_pattern = re.compile(r"🕒 (\d{1,2}:\d{2})-(\d{1,2}:\d{2})")
        
        # Разделяем текст на отдельные пары
        pairs = re.split(r'──────── \d+ ────────', schedule_text)
        
        for pair in pairs:
            if not pair.strip():
                continue
                
            # Ищем время в текущей паре
            times = time_pattern.search(pair)
            if times:
                start_time_str = times.group(1)  # "8:30"
                end_time_str = times.group(2)    # "10:00"
                
                # Добавляем ведущие нули если нужно
                if len(start_time_str) == 4:  # "8:30" -> "08:30"
                    start_time_str = "0" + start_time_str
                if len(end_time_str) == 4:    # "9:45" -> "09:45"
                    end_time_str = "0" + end_time_str
                
                # Создаем полные datetime объекты с сегодняшней датой и временной зоной
                today = datetime.now(MOSCOW_TZ).date()
                start_naive = datetime.combine(today, datetime.strptime(start_time_str, "%H:%M").time())
                end_naive = datetime.combine(today, datetime.strptime(end_time_str, "%H:%M").time())
                
                # Конвертируем в aware datetime с московской временной зоной
                start_time = MOSCOW_TZ.localize(start_naive)
                end_time = MOSCOW_TZ.localize(end_naive)
                
                lessons.append({
                    'start': start_time,
                    'end': end_time,
                    'text': pair.strip()
                })
                logger.info(f"Найдена пара: {start_time_str}-{end_time_str}")
                
    except Exception as e:
        logger.error(f"Ошибка парсинга расписания: {str(e)}")
    
    # Сортируем пары по времени начала
    return sorted(lessons, key=lambda x: x['start'])


def find_current_and_next_lesson(lessons: list, current_time: datetime):
    current = None
    next_ = None

    # Преобразуем current_time в datetime с сегодняшней датой
    today = current_time.date()

    for i, lesson in enumerate(lessons):
        # Создаем полные объекты datetime для начала и конца пары
        lesson_start = datetime.combine(today, lesson['start'])
        lesson_end = datetime.combine(today, lesson['end'])

        if lesson_start <= current_time < lesson_end:
            current = {
                'start': lesson_start,
                'end': lesson_end,
                'text': lesson['text']
            }
            if i + 1 < len(lessons):
                next_lesson = lessons[i + 1]
                next_ = {
                    'start': datetime.combine(today, next_lesson['start']),
                    'end': datetime.combine(today, next_lesson['end']),
                    'text': next_lesson['text']
                }
            break
        elif current_time < lesson_start:
            next_ = {
                'start': datetime.combine(today, lesson['start']),
                'end': datetime.combine(today, lesson['end']),
                'text': lesson['text']
            }
            break

    return current, next_


async def send_notification(user_id: int, current_lesson: dict, next_lesson: dict):
    notification_text = (
        f"⏳ Текущая пара заканчивается в {current_lesson['end'].strftime('%H:%M')}\n"  # Исправили здесь
        f"➡️ Следующая пара:\n{next_lesson['text']}"
    )

    try:
        await bot.send_message(user_id, notification_text)
        logger.info(f"Sent notification to {user_id}")
    except Exception as e:
        logger.error(f"Failed to send notification to {user_id}: {str(e)}")


# Запуск планировщика при старте бота



class CalendarNavigation:
    def __init__(self):
        self.now = datetime.now()
        self.current_year = self.now.year
        self.current_month = self.now.month

    async def create_calendar(self, year=None, month=None):
        if not year:
            year = self.current_year
        if not month:
            month = self.current_month

        builder = InlineKeyboardBuilder()
        today = self.now.day if (year == self.now.year and month == self.now.month) else None

        # Строка 1: Навигация по месяцам
        builder.row(
            InlineKeyboardButton(
                text="◀",
                callback_data=f"prev_{year}_{month}"  # Упрощенный формат
            ),
            InlineKeyboardButton(
                text=f"{month_name[month]} {year}",
                callback_data="ignore"
            ),
            InlineKeyboardButton(
                text="▶",
                callback_data=f"next_{year}_{month}"  # Упрощенный формат
            ),
            width=3
        )

        # Строка 2: Дни недели
        week_days = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]
        builder.row(*[
            InlineKeyboardButton(text=day, callback_data="ignore")
            for day in week_days
        ], width=7)

        # Строки 3-8: Дни месяца
        first_day = datetime(year, month, 1)
        start_weekday = first_day.weekday()  # 0 = Понедельник
        month_days = monthrange(year, month)[1]
        day_counter = 1

        # Добавляем пустые кнопки для начала месяца
        for _ in range(start_weekday):
            builder.add(InlineKeyboardButton(text=" ", callback_data="ignore"))

        # Добавляем дни месяца
        for day in range(1, month_days + 1):
            is_today = day == today
            text = f"•{day}•" if is_today else str(day)
            builder.add(InlineKeyboardButton(
                text=text,
                callback_data=f"day_{year}_{month:02d}_{day:02d}"
            ))

        # Добавляем пустые кнопки в конце
        total_cells = start_weekday + month_days
        if total_cells % 7 != 0:
            for _ in range(7 - (total_cells % 7)):
                builder.add(InlineKeyboardButton(text=" ", callback_data="ignore"))

        # Управляющие кнопки
        builder.row(
            InlineKeyboardButton(text="✅ Сегодня", callback_data="today"),
            InlineKeyboardButton(text="❌ Отмена", callback_data="cancel_calendar"),
            width=2
        )

        return builder.adjust(
            3,    # Навигация
            7,    # Дни недели
            *[7] * ((total_cells + 7 - 1) // 7),  # Строки с днями
            2     # Управляющие кнопки
        ).as_markup()


def main_keyboard(user_id: int):
    builder = ReplyKeyboardBuilder()
    group_id = db.get_user(user_id)

    if group_id:
        builder.button(text="📅 Получить расписание")
    builder.button(text="👥 Выбрать группу")  # Новая кнопка
    builder.button(text="⚙ Настройки")
    builder.button(text="ℹ Помощь")
    builder.button(text="💖 Поддержать бота")

    builder.adjust(2, 2, 1)
    return builder.as_markup(resize_keyboard=True)

@dp.message(Command("display"))
async def cmd_display(message: types.Message):
    """Быстрый доступ к настройкам отображения"""
    await cmd_display_settings(message)

@dp.message(lambda message: message.text == "⚙ Настройки")
async def cmd_settings(message: types.Message):
    await message.answer(
        "⚙ Настройки аккаунта:",
        reply_markup=settings_keyboard(message.from_user.id)
    )


@dp.message(lambda message: message.text.startswith(("🔔", "🔕")))
async def toggle_notifications(message: types.Message):
    user_id = message.from_user.id
    current_status = db.get_notifications_status(user_id)

    if current_status:
        db.disable_notifications(user_id)
        await message.answer("🔕 Уведомления отключены", reply_markup=settings_keyboard(user_id))
    else:
        db.enable_notifications(user_id)
        await message.answer("🔔 Уведомления включены", reply_markup=settings_keyboard(user_id))




@dp.message(lambda message: message.text == "⬅️ Назад")
async def cmd_back(message: types.Message):
    await message.answer(
        "Главное меню:",
        reply_markup=main_keyboard(message.from_user.id)
    )
@dp.message(CommandStart())
async def cmd_start(message: types.Message):
    welcome_text = f"""
    🎓 <b>Добро пожаловать, {message.from_user.first_name}!</b> 👋

    Я - бот-помощник для студентов Губкинского университета, созданный студентами для студентов! 

    🌟 <b>Что я умею:</b>
    📅 <i>Расписание занятий</i> - показываю ваше расписание с деталями и подгруппами
    🔔 <i>Уведомления</i> - напоминания о занятиях и важных событиях
    📱 <i>Удобный интерфейс</i> - простое управление через кнопки

    🛠 <b>Быстрый старт:</b>
    1. Выберите вашу группу через меню "👥 Выбрать группу"
    2. Получайте расписание одним нажатием!
    3. Настройте уведомления в разделе "⚙ Настройки"

    💡 <i>Этот бот создан студентами и не является официальным продуктом университета</i>
    📌 <i>Используйте меню внизу для навигации</i>
    """

    await message.answer(
        text=welcome_text,
        parse_mode=ParseMode.HTML,
        reply_markup=main_keyboard(message.from_user.id)
    )

@dp.message(Command('help'))

@dp.message(lambda message: message.text == "ℹ Помощь")
async def cmd_help(message: types.Message):
    help_text = """
    ℹ <b>Как пользоваться ботом</b>

    <b>1. Выберите группу</b>
    Нажмите "👥 Выбрать группу" → выберите факультет → введите номер группы

    <b>2. Получите расписание</b>
    Нажмите "📅 Получить расписание" → выберите дату в календаре

    <b>3. Настройте уведомления</b>
    Нажмите "⚙ Настройки" → включите уведомления и выберите время

    <b>📝 Обратная связь:</b>
    @chertolet24 - предложения и ошибки
    @HaJokerHa - предложения и ошибки

    💡 <i>Бот создан студентами для удобства студентов Губкинского университета</i>
    """
    
    await message.answer(
        help_text,
        parse_mode=ParseMode.HTML
    )



@dp.message(lambda message: message.text == "📅 Получить расписание")
async def cmd_schedule(message: types.Message, state: FSMContext):
    # Убираем клавиатуру сразу при начале обработки
    await message.answer("⏳ Ищем вашу группу...", reply_markup=ReplyKeyboardRemove())
    try:
        group_id = db.get_user(message.from_user.id)
        if not group_id:
            await message.answer("❌ Сначала выберите группу через меню '👥 Выбрать группу'")
            return

        await state.set_state(Form.waiting_date_calendar)
        # Отправляем календарь без основной клавиатуры
        await message.answer("📅 Выберите дату:",
                           reply_markup=await CalendarNavigation().create_calendar())
    except Exception as e:
        logger.error(f"Ошибка в cmd_schedule: {e}", exc_info=True)
        await message.answer("❌ Ошибка доступа к расписанию",
                           reply_markup=main_keyboard(message.from_user.id))

@dp.message(lambda message: message.text == "👥 Выбрать группу")
async def cmd_set_group(message: types.Message, state: FSMContext):
    await state.set_state(Form.waiting_faculty)
    builder = InlineKeyboardBuilder()
    for fid, name in FACULTIES.items():
        builder.button(text=name, callback_data=f"fac_{fid}")
    builder.adjust(1)
    await message.answer("🏛 Выберите ваш факультет:", reply_markup=builder.as_markup())

async def date_keyboard():
    builder = ReplyKeyboardBuilder()
    builder.button(text="Сегодня")
    builder.button(text="Завтра")
    builder.button(text="Ввести дату")
    builder.button(text="Отмена")
    builder.adjust(2, 1, 1)
    return builder.as_markup(resize_keyboard=True)

@dp.callback_query(Form.waiting_date_calendar)
async def process_calendar(callback: CallbackQuery, state: FSMContext):
    data = callback.data
    calendar = CalendarNavigation()
    user_id = callback.from_user.id

    try:
        if data == "today":
            date = datetime.now().strftime("%d-%m-%Y")
            try:
                await callback.message.edit_text("⏳ Загружаю расписание...", reply_markup=None)
            except Exception:
                pass
            
            result, keyboard = await get_schedule(user_id, date)
            # Теперь keyboard всегда будет определен
            await callback.message.answer(result, reply_markup=keyboard)
            await state.clear()
            return

        elif data == "cancel_calendar":
            try:
                await callback.message.edit_text("❌ Выбор даты отменен", reply_markup=None)
            except Exception:
                await callback.message.answer("❌ Выбор даты отменен")
            await state.clear()
            # Возвращаем к главному меню
            await callback.message.answer(
                "🏠 Главное меню:",
                reply_markup=main_keyboard(user_id)
            )
            return

        elif data.startswith('day_'):
            _, year, month, day = data.split('_')
            date = f"{day}-{month}-{year}"
            try:
                await callback.message.edit_text("⏳ Загружаю расписание...", reply_markup=None)
            except Exception:
                pass
            
            result, keyboard = await get_schedule(user_id, date)
            # Теперь keyboard всегда будет определен
            await callback.message.answer(result, reply_markup=keyboard)
            await state.clear()
            return

        # Исправленный формат обработки навигации
        elif data.startswith('prev_') or data.startswith('next_'):
            action, year, month = data.split('_')
            year = int(year)
            month = int(month)

            if action == "prev":
                month -= 1
                if month < 1:
                    month = 12
                    year -= 1
            else:
                month += 1
                if month > 12:
                    month = 1
                    year += 1

            markup = await calendar.create_calendar(year, month)
            await callback.message.edit_reply_markup(reply_markup=markup)
            await callback.answer()

    except Exception as e:
        logger.error(f"Calendar error: {str(e)}", exc_info=True)
        await callback.message.answer(
            "⚠ Произошла ошибка, попробуйте еще раз",
            reply_markup=main_keyboard(user_id)
        )
        await state.clear()


def settings_keyboard(user_id: int):
    builder = ReplyKeyboardBuilder()
    notifications_enabled = db.get_notifications_status(user_id)
    notify_time = db.get_notification_time(user_id)
    display_settings = db.get_display_settings(user_id)

    # Кнопка уведомлений
    notify_icon = "🔔" if notifications_enabled else "🔕"
    notify_text = "Отключить уведомления" if notifications_enabled else "Включить уведомления"
    builder.button(text=f"{notify_icon} {notify_text}")

    # Кнопка времени уведомлений
    builder.button(text=f"⏰ Текущее время: {notify_time}")

    # Кнопка настроек отображения
    builder.button(text="🎨 Настройки отображения")

    # Кнопка ручного обновления
    builder.button(text="🔄 Обновить расписание")

    # Кнопка удаления данных
    builder.button(text="🗑 Удалить мои данные")

    # Кнопка возврата
    builder.button(text="⬅️ Назад")

    builder.adjust(1, 1, 1, 1, 1, 1)
    return builder.as_markup(resize_keyboard=True)

def display_settings_keyboard(user_id: int):
    """Клавиатура настроек отображения"""
    display_settings = db.get_display_settings(user_id)
    builder = InlineKeyboardBuilder()
    
    # Формат ФИО
    teacher_format = "🟢 Полное" if display_settings['teacher_name_format'] == 'full' else "⚫ Краткое"
    builder.button(text=f"👤 ФИО: {teacher_format}", callback_data="toggle_teacher_format")
    
    # Консультации
    consultations = "🟢 Вкл" if display_settings['show_consultations'] else "⚫ Выкл"
    builder.button(text=f"💬 Консультации: {consultations}", callback_data="toggle_consultations")
    
    # Аудитории
    rooms = "🟢 Вкл" if display_settings['show_rooms'] else "⚫ Выкл"
    builder.button(text=f"🏫 Аудитории: {rooms}", callback_data="toggle_rooms")
    
    # Компактный режим
    compact = "🟢 Вкл" if display_settings['compact_mode'] else "⚫ Выкл"
    builder.button(text=f"📱 Компактно: {compact}", callback_data="toggle_compact")
    
    # Кнопки управления
    builder.button(text="✅ Применить", callback_data="apply_display_settings")
    builder.button(text="❌ Отмена", callback_data="cancel_display_settings")
    
    builder.adjust(1, 1, 1, 1, 2)
    return builder.as_markup()

@dp.message(lambda message: message.text.startswith(("🔔", "🔕")))
async def toggle_notifications(message: types.Message):
    user_id = message.from_user.id
    current_status = db.get_notifications_status(user_id)

    if current_status:
        db.disable_notifications(user_id)
        await message.answer("🔕 Уведомления отключены", reply_markup=main_keyboard(user_id))
    else:
        db.enable_notifications(user_id)
        await message.answer("🔔 Уведомления включены", reply_markup=main_keyboard(user_id))


@dp.message(lambda message: message.text == "🔄 Обновить расписание")
async def manual_refresh_schedule(message: types.Message):
    """Ручное обновление недельного кеша расписания не чаще 1 раза в 7 дней"""
    try:
        user_id = message.from_user.id
        group_id = db.get_user(user_id)
        if not group_id:
            await message.answer("❌ Сначала выберите группу через меню '👥 Выбрать группу'")
            return

        # Ограничение по времени: раз в 7 дней
        last_refresh = db.get_last_manual_refresh(user_id)
        now_dt = datetime.now(MOSCOW_TZ)
        if last_refresh:
            try:
                # Приводим last_refresh к TZ Москвы для корректного сравнения
                if last_refresh.tzinfo is None:
                    last_refresh = MOSCOW_TZ.localize(last_refresh)
            except Exception:
                pass
            if (now_dt - last_refresh) < timedelta(days=7):
                next_allowed = (last_refresh + timedelta(days=7)).astimezone(MOSCOW_TZ)
                await message.answer(
                    f"⏳ Слишком часто. Повторите после {next_allowed.strftime('%d-%m-%Y %H:%M')}"
                )
                return

        await message.answer("🔄 Обновляю кеш расписания на текущую неделю...")

        # Текущая дата и её ISO-страница
        today = datetime.now(MOSCOW_TZ)
        # Сбрасываем кеш текущей недели
        invalidate_week_cache(group_id, today)

        # Форсированная перезагрузка: запросим неделю и перезапишем кеш
        data = fetch_week_schedule_json(group_id, today)
        if not data or not data.get('state'):
            await message.answer("❌ Не удалось получить актуальное расписание. Попробуйте позже.")
            return

        # Сохраняем время успешного обновления
        db.set_last_manual_refresh(user_id, now_dt)

        await message.answer("✅ Кеш недели обновлен! Теперь расписание будет актуальным.")
    except Exception as e:
        logger.error(f"Ошибка ручного обновления расписания: {e}", exc_info=True)
        await message.answer("❌ Произошла ошибка при обновлении расписания")

@dp.callback_query(Form.waiting_faculty)
async def process_faculty(callback: CallbackQuery, state: FSMContext):
    try:
        faculty_id = int(callback.data.split("_")[1])
        await state.update_data(faculty_id=faculty_id)
        await state.set_state(Form.waiting_group_input)

        groups = fetch_groups(faculty_id)
        group_list = "\n".join([f"▪ {code}" for code, _ in groups[:5]])

        await callback.message.edit_text(
            "👥 Введите номер вашей группы (например: МР-23-10):\n\n"
            f"Примеры групп факультета:\n{group_list}\n\n"
            "ℹ Введите полное название группы"
        )

    except Exception as e:
        logger.error(f"Ошибка выбора факультета: {str(e)}")
        await callback.message.edit_text("❌ Ошибка при выборе факультета")
        await state.clear()


@dp.message(Form.waiting_group_input)
async def process_group_input(message: types.Message, state: FSMContext):
    user_input = message.text.strip().upper()
    data = await state.get_data()
    faculty_id = data.get('faculty_id')

    groups = fetch_groups(faculty_id)
    found_groups = [g for g in groups if user_input == g[0].upper()]

    if not found_groups:
        found_groups = [g for g in groups if user_input in g[0].upper()]

    if len(found_groups) == 1:
        group_id = found_groups[0][1]
        db.update_group(message.from_user.id, group_id)
        await message.answer(
            f"✅ Группа {found_groups[0][0]} выбрана и сохранена!",
            reply_markup=main_keyboard(message.from_user.id)  # ОБНОВЛЕНИЕ КЛАВИАТУРЫ
        )
        await state.clear()
    elif len(found_groups) > 1:
        buttons = [
            [InlineKeyboardButton(text=code, callback_data=f"gr_{id_}")]
            for code, id_ in found_groups[:5]
        ]
        await message.answer(
            "🔍 Найдено несколько совпадений:",
            reply_markup=InlineKeyboardMarkup(inline_keyboard=buttons)
        )
    else:
        await message.answer("❌ Группа не найдена. Проверьте ввод и попробуйте снова:")

@dp.message(lambda message: message.text.startswith("⏰ Текущее время:"))
async def change_notification_time(message: types.Message, state: FSMContext):
    await state.set_state(Form.waiting_notification_time)
    await message.answer(
        "🕒 Введите новое время уведомлений в формате ЧЧ:ММ (например 08:30):",
        reply_markup=ReplyKeyboardRemove()
    )


@dp.message(lambda message: message.text == "🗑 Удалить мои данные")
async def delete_user_data(message: types.Message):
    """Удаление всех данных пользователя"""
    user_id = message.from_user.id
    
    # Создаем инлайн-клавиатуру для подтверждения
    builder = InlineKeyboardBuilder()
    builder.button(text="✅ Да, удалить", callback_data=f"confirm_delete_{user_id}")
    builder.button(text="❌ Отмена", callback_data="cancel_delete")
    builder.adjust(1)
    
    await message.answer(
        "⚠️ <b>Внимание!</b>\n\n"
        "Вы действительно хотите удалить все ваши данные?\n\n"
        "Это действие удалит:\n"
        "• Вашу группу\n"
        "• Настройки уведомлений\n"
        "• Все персональные данные\n\n"
        "<i>Это действие нельзя отменить!</i>",
        reply_markup=builder.as_markup(),
        parse_mode=ParseMode.HTML
    )



@dp.message(Form.waiting_notification_time)
async def process_notification_time(message: types.Message, state: FSMContext):
    try:
        time_str = message.text.strip()
        datetime.strptime(time_str, "%H:%M")
        db.set_notification_time(message.from_user.id, time_str)
        await message.answer(
            f"✅ Время уведомлений изменено на {time_str}",
            reply_markup=settings_keyboard(message.from_user.id)
        )
        await state.clear()
    except ValueError:
        await message.answer("❌ Неверный формат времени! Используйте ЧЧ:ММ (например 09:15)")




@dp.callback_query(lambda c: c.data.startswith("gr_"))
async def confirm_group(callback: CallbackQuery):
    group_id = int(callback.data.split("_")[1])
    db.update_group(callback.from_user.id, group_id)

    # Удаляем инлайн-кнопки и показываем новое меню
    await callback.message.delete()
    await callback.message.answer(
        f"✅ Группа успешно выбрана!",
        reply_markup=main_keyboard(callback.from_user.id)  # ОБНОВЛЕНИЕ КЛАВИАТУРЫ
    )


@dp.callback_query(lambda c: c.data == "back_to_calendar")
async def back_to_calendar(callback: CallbackQuery, state: FSMContext):
    """Обработчик кнопки возврата в календарь"""
    await state.set_state(Form.waiting_date_calendar)
    await callback.message.edit_text(
        "📅 Выберите дату:",
        reply_markup=await CalendarNavigation().create_calendar()
    )
    await callback.answer()


@dp.callback_query(lambda c: c.data == "back_to_main")
async def back_to_main(callback: CallbackQuery, state: FSMContext):
    """Обработчик кнопки возврата в главное меню"""
    await state.clear()
    await callback.message.delete()
    await callback.message.answer(
        "🏠 Главное меню:",
        reply_markup=main_keyboard(callback.from_user.id)
    )
    await callback.answer()


@dp.callback_query(lambda c: c.data.startswith("nav_day_"))
async def navigate_day(callback: CallbackQuery, state: FSMContext):
    """Навигация по дням из экрана расписания (◀ ▶)"""
    try:
        parts = callback.data.split('_')
        # Ожидаемый формат: nav_day_{prev|next}_{dd-mm-YYYY}
        if len(parts) < 4:
            await callback.answer()
            return

        direction = parts[2]
        date_str = parts[3]

        try:
            current_date = datetime.strptime(date_str, "%d-%m-%Y")
        except ValueError:
            await callback.answer("Неверная дата", show_alert=False)
            return

        delta = timedelta(days=-1 if direction == 'prev' else 1)
        new_date = current_date + delta
        new_date_str = new_date.strftime("%d-%m-%Y")

        result, keyboard = await get_schedule(callback.from_user.id, new_date_str)

        try:
            # Обновляем и текст, и клавиатуру одним вызовом
            await callback.message.edit_text(result, reply_markup=keyboard)
        except Exception:
            # Если не удалось отредактировать (например, слишком старое сообщение), отправим новое
            await callback.message.answer(result, reply_markup=keyboard)

        await callback.answer()
    except Exception as e:
        logger.error(f"Ошибка навигации по дням: {e}", exc_info=True)
        await callback.answer("Ошибка", show_alert=False)

@dp.callback_query(lambda c: c.data.startswith("confirm_delete_"))
async def confirm_delete_user(callback: CallbackQuery):
    """Подтверждение удаления данных пользователя"""
    user_id = callback.from_user.id
    
    try:
        # Удаляем данные пользователя из базы
        db.cursor.execute('DELETE FROM users WHERE user_id = ?', (user_id,))
        db.cursor.execute('DELETE FROM user_settings WHERE user_id = ?', (user_id,))
        db.conn.commit()
        
        await callback.message.edit_text(
            "✅ <b>Все ваши данные успешно удалены!</b>\n\n"
            "Вы можете начать заново, выбрав группу в главном меню.",
            parse_mode=ParseMode.HTML
        )
        
        # Отправляем новое сообщение с главным меню
        await callback.message.answer(
            "🏠 Главное меню:",
            reply_markup=main_keyboard(user_id)
        )
        
        logger.info(f"User {user_id} deleted all their data")
        
    except Exception as e:
        logger.error(f"Error deleting user data for {user_id}: {str(e)}")
        await callback.message.edit_text(
            "❌ Произошла ошибка при удалении данных. Попробуйте позже.",
            reply_markup=main_keyboard(user_id)
        )
    
    await callback.answer()


@dp.callback_query(lambda c: c.data == "cancel_delete")
async def cancel_delete_user(callback: CallbackQuery):
    """Отмена удаления данных пользователя"""
    await callback.message.edit_text(
        "❌ Удаление данных отменено.",
        reply_markup=settings_keyboard(callback.from_user.id)
    )
    await callback.answer()

@dp.message(lambda message: message.text == "🎨 Настройки отображения")
async def cmd_display_settings(message: types.Message):
    """Показываем настройки отображения"""
    display_settings = db.get_display_settings(message.from_user.id)
    
    settings_text = (
        "🎨 <b>Настройки отображения расписания</b>\n\n"
        f"👤 <b>Формат ФИО:</b> {'Полный (Иванов Иван Иванович)' if display_settings['teacher_name_format'] == 'full' else 'Краткий (Иванов И.И.)'}\n"
        f"💬 <b>Консультации:</b> {'Показывать' if display_settings['show_consultations'] else 'Скрывать'}\n"
        f"🏫 <b>Аудитории:</b> {'Показывать' if display_settings['show_rooms'] else 'Скрывать'}\n"
        f"📱 <b>Режим:</b> {'Компактный' if display_settings['compact_mode'] else 'Полный'}\n\n"
        "Измените нужные настройки:"
    )
    
    await message.answer(settings_text, reply_markup=display_settings_keyboard(message.from_user.id))

@dp.callback_query(lambda c: c.data.startswith("toggle_"))
async def toggle_display_setting(callback: CallbackQuery):
    """Переключение настроек отображения"""
    user_id = callback.from_user.id
    setting = callback.data.replace("toggle_", "")
    
    current_settings = db.get_display_settings(user_id)
    
    if setting == "teacher_format":
        new_value = 'full' if current_settings['teacher_name_format'] == 'short' else 'short'
        db.set_display_setting(user_id, 'teacher_name_format', new_value)
    elif setting == "consultations":
        new_value = not current_settings['show_consultations']
        db.set_display_setting(user_id, 'show_consultations', new_value)
    elif setting == "rooms":
        new_value = not current_settings['show_rooms']
        db.set_display_setting(user_id, 'show_rooms', new_value)
    elif setting == "compact":
        new_value = not current_settings['compact_mode']
        db.set_display_setting(user_id, 'compact_mode', new_value)
    
    # Обновляем сообщение с новыми настройками
    display_settings = db.get_display_settings(user_id)
    settings_text = (
        "🎨 <b>Настройки отображения расписания</b>\n\n"
        f"👤 <b>Формат ФИО:</b> {'Полный (Иванов Иван Иванович)' if display_settings['teacher_name_format'] == 'full' else 'Краткий (Иванов И.И.)'}\n"
        f"💬 <b>Консультации:</b> {'Показывать' if display_settings['show_consultations'] else 'Скрывать'}\n"
        f"🏫 <b>Аудитории:</b> {'Показывать' if display_settings['show_rooms'] else 'Скрывать'}\n"
        f"📱 <b>Режим:</b> {'Компактный' if display_settings['compact_mode'] else 'Полный'}\n\n"
        "Измените нужные настройки:"
    )
    
    await callback.message.edit_text(settings_text, reply_markup=display_settings_keyboard(user_id))
    await callback.answer()

@dp.callback_query(lambda c: c.data == "apply_display_settings")
async def apply_display_settings(callback: CallbackQuery):
    """Применение настроек отображения"""
    await callback.message.edit_text("✅ Настройки отображения сохранены!")
    await callback.answer()

@dp.callback_query(lambda c: c.data == "cancel_display_settings")
async def cancel_display_settings(callback: CallbackQuery):
    """Отмена изменения настроек отображения"""
    await callback.message.edit_text("❌ Изменения отменены")
    await callback.answer()

@dp.callback_query(lambda c: c.data == "display_settings")
async def show_display_settings_from_schedule(callback: CallbackQuery):
    """Показ настроек отображения из расписания"""
    display_settings = db.get_display_settings(callback.from_user.id)
    
    settings_text = (
        "🎨 <b>Настройки отображения расписания</b>\n\n"
        f"👤 <b>Формат ФИО:</b> {'Полный (Иванов Иван Иванович)' if display_settings['teacher_name_format'] == 'full' else 'Краткий (Иванов И.И.)'}\n"
        f"💬 <b>Консультации:</b> {'Показывать' if display_settings['show_consultations'] else 'Скрывать'}\n"
        f"🏫 <b>Аудитории:</b> {'Показывать' if display_settings['show_rooms'] else 'Скрывать'}\n"
        f"📱 <b>Режим:</b> {'Компактный' if display_settings['compact_mode'] else 'Полный'}\n\n"
        "Измените нужные настройки:"
    )
    
    await callback.message.answer(settings_text, reply_markup=display_settings_keyboard(callback.from_user.id))
    await callback.answer()


@dp.message(lambda message: message.text == "Отмена")
async def cmd_cancel(message: types.Message, state: FSMContext):
    await state.clear()
    await message.answer("❌ Действие отменено", reply_markup=main_keyboard(message.from_user.id))

@dp.message(Command("delete_me"))
async def cmd_delete_me(message: types.Message):
    db.cursor.execute('DELETE FROM users WHERE user_id = ?', (message.from_user.id,))
    db.conn.commit()
    await message.answer("✅ Все ваши данные успешно удалены!",
                       reply_markup=main_keyboard(message.from_user.id))


@dp.message(Command("broadcast"))
async def cmd_broadcast(message: types.Message):
    """Отправка сообщения всем пользователям (для администраторов).
    Использование: /broadcast текст сообщения
    ADMIN_IDS задаются в .env как ADMIN_IDS="123,456"
    """
    user_id = message.from_user.id
    if user_id not in ADMIN_IDS:
        await message.answer("❌ Команда доступна только администраторам.")
        return

    # Текст после команды
    parts = message.text.split(maxsplit=1)
    if len(parts) < 2 or not parts[1].strip():
        await message.answer("⚠ Укажите текст сообщения: /broadcast Текст рассылки")
        return

    broadcast_text = parts[1].strip()

    all_user_ids = set(db.get_all_user_ids())
    # Также добавим пользователей из настроек уведомлений, если они есть
    try:
        db.cursor.execute('SELECT user_id FROM user_settings')
        all_user_ids.update([row[0] for row in db.cursor.fetchall()])
    except Exception:
        pass

    if not all_user_ids:
        await message.answer("ℹ Нет пользователей для рассылки.")
        return

    sent = 0
    failed = 0
    for uid in all_user_ids:
        try:
            await bot.send_message(uid, broadcast_text)
            sent += 1
            # небольшая уступка по лимитам телеграма, если нужно можно добавить asyncio.sleep
        except Exception as e:
            failed += 1
            logger.warning(f"Broadcast to {uid} failed: {e}")

    await message.answer(f"✅ Рассылка завершена. Успешно: {sent}, Ошибок: {failed}")


@dp.message(Command("tex"))
async def cmd_tex(message: types.Message):
    """Быстрая рассылка сообщения о завершении техработ (для администраторов).
    Использование: /tex
    """
    user_id = message.from_user.id
    # Один раз глобально для всех: если флаг уже установлен, блокируем не-админов
    already_sent = db.get_flag('tex_sent') == 'true'
    if already_sent and user_id not in ADMIN_IDS:
        await message.answer("ℹ Сообщение уже было разослано ранее.")
        return

    announce_text = (
        "ℹ️ Технические работы завершены.\n"
        "Бот снова доступен — можно пользоваться! Спасибо за ожидание."
    )

    all_user_ids = set(db.get_all_user_ids())
    try:
        db.cursor.execute('SELECT user_id FROM user_settings')
        all_user_ids.update([row[0] for row in db.cursor.fetchall()])
    except Exception:
        pass

    if not all_user_ids:
        await message.answer("ℹ Нет пользователей для рассылки.")
        return

    sent = 0
    failed = 0
    for uid in all_user_ids:
        try:
            await bot.send_message(uid, announce_text)
            sent += 1
        except Exception as e:
            failed += 1
            logger.warning(f"TEX announce to {uid} failed: {e}")

    # Устанавливаем флаг после успешной рассылки (даже частично)
    db.set_flag('tex_sent', 'true')
    await message.answer(f"✅ Сообщение отправлено. Успешно: {sent}, Ошибок: {failed}")

@dp.message(Command("cancel"))
async def cmd_cancel_feedback(message: types.Message, state: FSMContext):
    """Отмена текущего действия"""
    current_state = await state.get_state()
    if current_state == Form.waiting_notification_time:
        await state.clear()
        await message.answer(
            "❌ Изменение времени уведомлений отменено",
            reply_markup=settings_keyboard(message.from_user.id)
        )
    else:
        await state.clear()
        await message.answer(
            "❌ Действие отменено",
            reply_markup=main_keyboard(message.from_user.id)
        )

@dp.message(Command("update_notifications"))
async def cmd_update_notifications(message: types.Message):
    """Обновляет планирование уведомлений"""
    try:
        await schedule_daily_notifications()
        await message.answer("✅ Уведомления обновлены!", 
                           reply_markup=main_keyboard(message.from_user.id))
    except Exception as e:
        logger.error(f"Ошибка обновления уведомлений: {str(e)}")
        await message.answer("❌ Ошибка обновления уведомлений",
                           reply_markup=main_keyboard(message.from_user.id))

@dp.message(Command("tor_status"))
async def cmd_tor_status(message: types.Message):
    """Проверяет статус Tor (для администраторов)"""
    user_id = message.from_user.id
    if user_id not in ADMIN_IDS:
        await message.answer("❌ Команда доступна только администраторам.")
        return
    
    if check_tor_connection():
        await message.answer("✅ Tor доступен и работает")
    else:
        await message.answer("❌ Tor недоступен или не запущен")

@dp.message(Command("tor_renew"))
async def cmd_tor_renew(message: types.Message):
    """Обновляет цепь Tor (для администраторов)"""
    user_id = message.from_user.id
    if user_id not in ADMIN_IDS:
        await message.answer("❌ Команда доступна только администраторам.")
        return
    
    if renew_tor_circuit():
        await message.answer("✅ Цепь Tor обновлена")
    else:
        await message.answer("❌ Ошибка обновления цепи Tor")
        
@dp.message(Command("update_notifs"))
async def cmd_update_notifs(message: types.Message):
    """Принудительное обновление уведомлений с детальным отчетом"""
    user_id = message.from_user.id
    
    try:
        # Проверяем, включены ли уведомления
        if not db.get_notifications_status(user_id):
            await message.answer("❌ Уведомления отключены. Включите их в настройках.")
            return
        
        # Проверяем, установлена ли группа
        if not db.get_user(user_id):
            await message.answer("❌ Группа не установлена. Сначала выберите группу.")
            return
        
        # Обновляем уведомления
        await message.answer("🔄 Обновляю уведомления...")
        await plan_user_notifications(user_id)
        
        # Получаем актуальный статус
        today = datetime.now(MOSCOW_TZ).strftime("%d-%m-%Y")
        schedule_text, _ = await get_schedule(user_id, today)
        lessons = parse_schedule(schedule_text)
        
        # Считаем запланированные уведомления
        current_time = datetime.now(MOSCOW_TZ)
        planned_count = 0
        for lesson in lessons:
            notification_time = lesson['end'] - timedelta(minutes=10)
            if notification_time > current_time:
                planned_count += 1
        
        response = (
            f"✅ <b>Уведомления обновлены!</b>\n\n"
            f"📊 <b>Статус:</b>\n"
            f"• Найдено пар: {len(lessons)}\n"
            f"• Активных уведомлений: {planned_count}\n"
            f"• Текущее время: {current_time.strftime('%H:%M')}\n\n"
        )
        
        if planned_count > 0:
            response += f"🔔 <b>Уведомления запланированы на:</b>\n"
            for i, lesson in enumerate(lessons):
                notification_time = lesson['end'] - timedelta(minutes=10)
                if notification_time > current_time:
                    response += f"• {notification_time.strftime('%H:%M')} (пара {i+1})\n"
        else:
            response += "ℹ️ <b>На сегодня уведомлений нет</b> (все уже прошли или пар нет)\n\n"
            response += "💡 Уведомления автоматически обновятся завтра в 00:01"
        
        await message.answer(response, parse_mode=ParseMode.HTML)
        
    except Exception as e:
        logger.error(f"Ошибка обновления уведомлений для {user_id}: {str(e)}")
        await message.answer("❌ Ошибка обновления уведомлений")
@dp.message(lambda message: message.text == "💖 Поддержать бота")
async def cmd_support(message: types.Message):
    """Информация о поддержке бота"""
    support_text = """
🤝 <b>Поддержка бота</b>

💡 <i>Этот бот создан студентами для студентов и не является официальным продуктом Губкинского университета.</i>

🌟 <b>Бот полностью бесплатен</b> и не требует денег за использование. Однако если вы хотите поддержать развитие проекта, вы можете сделать добровольное пожертвование.

🔄 <b>На что пойдут средства:</b>
• Хостинг и обслуживание серверов
• Разработка новых функций
• Улучшение стабильности работы

💳 <b>Ссылка для поддержки:</b>
https://pay.cloudtips.ru/p/254077fd

🙏 <b>Спасибо за вашу поддержку!</b>

<i>Помните: бот остается бесплатным независимо от пожертвований.</i>
    """
    
    # Создаем инлайн-кнопку для быстрого перехода
    builder = InlineKeyboardBuilder()
    builder.button(text="💖 Поддержать проект", url="https://pay.cloudtips.ru/p/254077fd")
    builder.adjust(1)
    
    await message.answer(
        support_text,
        parse_mode=ParseMode.HTML,
        reply_markup=builder.as_markup()
    )
        
@dp.message(Command("preload_groups"))
async def cmd_preload_groups(message: types.Message):
    """Предварительная загрузка всех групп (для администраторов)"""
    user_id = message.from_user.id
    if user_id not in ADMIN_IDS:
        await message.answer("❌ Команда доступна только администраторам.")
        return
    
    await message.answer("🔄 Начинаю предварительную загрузку групп для всех факультетов...")
    
    loaded_count = 0
    error_count = 0
    
    for faculty_id in FACULTIES.keys():
        try:
            groups = fetch_groups(faculty_id)
            if groups:
                loaded_count += 1
                logger.info(f"Загружены группы для факультета {faculty_id}: {len(groups)} групп")
            else:
                error_count += 1
                logger.warning(f"Не удалось загрузить группы для факультета {faculty_id}")
        except Exception as e:
            error_count += 1
            logger.error(f"Ошибка загрузки групп для факультета {faculty_id}: {e}")
    
    await message.answer(
        f"✅ Предварительная загрузка завершена!\n\n"
        f"• Успешно загружено: {loaded_count} факультетов\n"
        f"• Ошибок: {error_count}\n"
        f"• Всего факультетов: {len(FACULTIES)}"
    )

@dp.message(Command("check_notifications"))
async def cmd_check_notifications(message: types.Message):
    """Детальная проверка статуса уведомлений"""
    user_id = message.from_user.id
    
    try:
        # Собираем информацию о статусе уведомлений
        notifications_enabled = db.get_notifications_status(user_id)
        notification_time = db.get_notification_time(user_id)
        group_id = db.get_user(user_id)
        
        # Получаем расписание на сегодня для проверки
        today = datetime.now(MOSCOW_TZ).strftime("%d-%m-%Y")
        schedule_text, _ = await get_schedule(user_id, today)
        
        # Парсим расписание для получения информации о парах
        lessons = parse_schedule(schedule_text)
        
        # Формируем детальный отчет
        report = f"🔍 <b>Детальная проверка уведомлений</b>\n\n"
        
        # Статус основных настроек
        report += f"📊 <b>Основные настройки:</b>\n"
        report += f"• Группа: {'✅ Установлена' if group_id else '❌ Не установлена'}\n"
        report += f"• Уведомления: {'✅ Включены' if notifications_enabled else '❌ Выключены'}\n"
        report += f"• Время утренних уведомлений: {notification_time}\n\n"
        
        # Информация о расписании на сегодня
        report += f"📅 <b>Расписание на сегодня ({today}):</b>\n"
        if "не найдено" in schedule_text or "недоступно" in schedule_text:
            report += "• ❌ Расписание недоступно или занятий нет\n"
        elif lessons:
            report += f"• ✅ Найдено пар: {len(lessons)}\n"
            
            # Детальная информация о каждой паре и уведомлениях
            report += f"\n🔔 <b>Детали уведомлений:</b>\n"
            current_time = datetime.now(MOSCOW_TZ)
            planned_notifications = 0
            
            for i, lesson in enumerate(lessons):
                notification_time = lesson['end'] - timedelta(minutes=10)
                next_lesson = lessons[i + 1] if i + 1 < len(lessons) else None
                
                if notification_time > current_time:
                    planned_notifications += 1
                    status = "✅ Будет уведомление" if next_lesson else "✅ Последняя пара"
                    report += f"• {status} в {notification_time.strftime('%H:%M')} (пара {lesson['start'].strftime('%H:%M')}-{lesson['end'].strftime('%H:%M')})\n"
                else:
                    report += f"• ⏰ Уже прошло в {notification_time.strftime('%H:%M')} (пара {lesson['start'].strftime('%H:%M')}-{lesson['end'].strftime('%H:%M')})\n"
            
            if planned_notifications == 0:
                report += "• ℹ️ Нет активных уведомлений на сегодня\n"
            else:
                report += f"• 📋 Всего активных уведомлений: {planned_notifications}\n"
        else:
            report += "• ℹ️ Пар на сегодня нет\n"
        
        # Проверка планировщика
        report += f"\n⚙️ <b>Система уведомлений:</b>\n"
        if notification_scheduler and notification_scheduler.running:
            jobs = notification_scheduler.get_jobs()
            user_jobs = [job for job in jobs if f"lesson_notif_{user_id}" in job.id]
            report += f"• ✅ Планировщик работает\n"
            report += f"• 🔧 Ваших активных уведомлений в системе: {len(user_jobs)}\n"
            
            # Показываем ближайшие уведомления
            if user_jobs:
                report += f"• 🕒 Ближайшие уведомления:\n"
                for job in sorted(user_jobs, key=lambda j: j.next_run_time):
                    next_run = job.next_run_time.astimezone(MOSCOW_TZ) if job.next_run_time else None
                    if next_run:
                        report += f"  - {next_run.strftime('%H:%M')}\n"
        else:
            report += f"• ❌ Планировщик не работает\n"
        
        # Рекомендации
        report += f"\n💡 <b>Рекомендации:</b>\n"
        if not group_id:
            report += "• Установите группу через '👥 Выбрать группу'\n"
        if not notifications_enabled:
            report += "• Включите уведомления в настройках\n"
        if not lessons:
            report += "• На сегодня пар нет - уведомления не нужны\n"
        elif planned_notifications == 0 and lessons:
            report += "• Все уведомления на сегодня уже прошли\n"
            report += "• Уведомления автоматически обновятся завтра\n"
        
        # Кнопка для принудительного обновления
        report += f"\n🔄 Используйте /update_notifs для принудительного обновления уведомлений"
        
        await message.answer(report, parse_mode=ParseMode.HTML)
        
    except Exception as e:
        logger.error(f"Ошибка проверки уведомлений для {user_id}: {str(e)}")
        await message.answer("❌ Произошла ошибка при проверке уведомлений")

async def main():
    global notification_scheduler
    
    logger.info("Запуск бота...")
    
    # Основной планировщик для ежедневных задач
    scheduler = AsyncIOScheduler(timezone=MOSCOW_TZ)

    # Планируем обновление уведомлений на день в 00:01
    scheduler.add_job(
        schedule_daily_notifications,
        trigger=CronTrigger(hour=0, minute=1, timezone=MOSCOW_TZ),
        max_instances=1
    )

    # Ежедневные утренние уведомления
    scheduler.add_job(
        send_daily_notifications,
        trigger=CronTrigger(hour="*", minute=0, timezone=MOSCOW_TZ),
        max_instances=5
    )

    scheduler.start()
    
    # Инициализируем планировщик уведомлений о парах
    notification_scheduler = AsyncIOScheduler(timezone=MOSCOW_TZ)
    notification_scheduler.start()
    
    # Планируем уведомления сразу при запуске
    logger.info("Первоначальное планирование уведомлений...")
    await schedule_daily_notifications()
    
    await bot.delete_webhook()
    logger.info("Бот запущен и готов к работе!")
    await dp.start_polling(bot)


async def send_daily_notifications():
    now = datetime.now(MOSCOW_TZ)
    current_time = now.strftime("%H:%M")

    for user_id in db.get_users_with_notifications():
        user_time = db.get_notification_time(user_id)
        if current_time == user_time:
            await send_daily_schedule(user_id)


async def send_daily_schedule(user_id: int):
    try:
        today = datetime.now(MOSCOW_TZ).strftime("%d-%m-%Y")
        schedule_text, _ = await get_schedule(user_id, today)

        if "не найдено" in schedule_text or "недоступно" in schedule_text:
            return

        await bot.send_message(
            user_id,
            f"🌅 Доброе утро! Ваше расписание на сегодня:\n\n{schedule_text}"
        )
        logger.info(f"Sent daily notification to {user_id}")

    except Exception as e:
        logger.error(f"Error sending daily notification to {user_id}: {str(e)}")

if __name__ == '__main__':
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
#!/usr/bin/env python3
"""vc.py의 차툰 신호 명령(창문·공조·시트 열선·트렁크) 시험.

에뮬레이터 없이 돈다. 가상 콘솔을 흉내 낸 작은 서버를 띄워, 명령이 맞는 길과 이름으로 가는지와
적용 확인이 상태를 맞게 읽는지 본다. 가짜 서버의 상태 모양은 2026-11-12 탐침 기록
(research/experiments/2026-11-12-cartoon-probe)과 콘솔의 명령 목록(GET /vehicle/catalog)을 따랐다.
실제 콘솔에서 되는지는 이 시험으로 알 수 없다(에뮬레이터에서 `python3 tools/vc.py set …`으로 따로 본다).

실행: python3 tools/test_vc.py
"""
from __future__ import annotations

import json
import os
import sys
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vc  # noqa: E402

# 콘솔이 받는 이름 → 상태에서 읽히는 자리
WINDOWS = {"window_fl": "front_left", "window_fr": "front_right", "window_rl": "rear_left", "window_rr": "rear_right"}


class FakeConsole:
    """가상 콘솔이 하는 일 가운데 이 시험에 필요한 만큼만 흉내 낸다."""

    def __init__(self) -> None:
        self.state = {
            "odometer": {"emulator_active": True, "traveled_distance_km": 100.0},
            "body": {
                "windows": {area: 0 for area in WINDOWS.values()},
                "closures": {"trunk": False, "frunk": False},
                "wipers": {"front": {"mode": "Off"}},
                "door_locks": {"all": True},
            },
            "hvac": {"driver": {"temp_c": 22.0, "seat_temp": 0}, "passenger": {"temp_c": 22.0, "seat_temp": 0}, "fan_speed": 3},
        }
        self.posts: list[tuple[str, dict[str, str]]] = []
        # 고장 흉내: 여기 적힌 이름의 값은 받아도 상태에 반영하지 않는다.
        self.ignored: set[str] = set()

    def apply(self, path: str, params: dict[str, str]) -> None:
        self.posts.append((path, params))
        body, hvac = self.state["body"], self.state["hvac"]
        for name, value in params.items():
            if name in self.ignored:
                continue
            if path == "/vehicle/body" and name in WINDOWS:
                body["windows"][WINDOWS[name]] = int(value)
            elif path == "/vehicle/body" and name == "trunk":
                body["closures"]["trunk"] = value == "true"
            elif path == "/vehicle/body" and name == "front_wiper":
                body["wipers"]["front"]["mode"] = value
            elif path == "/vehicle/hvac" and name == "driver_temp":
                hvac["driver"]["temp_c"] = float(value)
            elif path == "/vehicle/hvac" and name == "fan_speed":
                hvac["fan_speed"] = int(value)
            elif path == "/vehicle/hvac" and name == "driver_seat":
                hvac["driver"]["seat_temp"] = int(value)


def serve(fake: FakeConsole) -> ThreadingHTTPServer:
    class Handler(BaseHTTPRequestHandler):
        def _reply(self, body: dict) -> None:
            data = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:  # noqa: N802
            self._reply(fake.state if self.path == "/vehicle/state" else {})

        def do_POST(self) -> None:  # noqa: N802
            url = urllib.parse.urlsplit(self.path)
            if url.path != "/browser/session":
                fake.apply(url.path, dict(urllib.parse.parse_qsl(url.query)))
            self._reply({})

        def log_message(self, *_: object) -> None:
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


class CartoonSignalCommands(unittest.TestCase):
    def setUp(self) -> None:
        self.fake = FakeConsole()
        self.server = serve(self.fake)
        self._base, self._timeout = vc.BASE, vc.VERIFY_TIMEOUT_S
        vc.BASE = f"http://127.0.0.1:{self.server.server_address[1]}"
        vc.VERIFY_TIMEOUT_S = 0.6
        self.console = vc.Console().__enter__()

    def tearDown(self) -> None:
        self.console.__exit__(None, None, None)
        self.server.shutdown()
        self.server.server_close()
        vc.BASE, vc.VERIFY_TIMEOUT_S = self._base, self._timeout

    def test_windows_moves_all_four_and_verifies(self) -> None:
        self.console.windows(100)
        self.assertEqual(self.fake.posts, [("/vehicle/body", {name: "100" for name in WINDOWS})])
        self.assertEqual(set(self.fake.state["body"]["windows"].values()), {100})
        self.console.windows(0)
        self.assertEqual(set(self.fake.state["body"]["windows"].values()), {0})

    def test_windows_fails_when_one_window_does_not_follow(self) -> None:
        self.fake.ignored = {"window_rr"}
        with self.assertRaisesRegex(vc.ToolError, "창문 100: 명령을 보냈지만 적용되지 않았습니다"):
            self.console.windows(100)

    def test_cabin_temp_sends_whole_degrees_as_integers(self) -> None:
        self.console.cabin_temp(17)
        self.assertEqual(self.fake.posts[-1], ("/vehicle/hvac", {"driver_temp": "17"}))
        self.assertEqual(self.fake.state["hvac"]["driver"]["temp_c"], 17.0)
        self.console.cabin_temp(26.5)
        self.assertEqual(self.fake.posts[-1], ("/vehicle/hvac", {"driver_temp": "26.5"}))
        # 조수석은 건드리지 않는다.
        self.assertEqual(self.fake.state["hvac"]["passenger"]["temp_c"], 22.0)

    def test_fan_seat_heat_and_trunk(self) -> None:
        self.console.fan(8)
        self.console.seat_heat(3)
        self.console.seat_heat(-2)
        self.console.trunk(True)
        self.assertEqual(
            self.fake.posts,
            [
                ("/vehicle/hvac", {"fan_speed": "8"}),
                ("/vehicle/hvac", {"driver_seat": "3"}),
                ("/vehicle/hvac", {"driver_seat": "-2"}),
                ("/vehicle/body", {"trunk": "true"}),
            ],
        )
        self.assertEqual(self.fake.state["hvac"]["fan_speed"], 8)
        self.assertEqual(self.fake.state["hvac"]["driver"]["seat_temp"], -2)
        self.assertIs(self.fake.state["body"]["closures"]["trunk"], True)
        self.console.trunk(False)
        self.assertIs(self.fake.state["body"]["closures"]["trunk"], False)

    def test_out_of_range_values_are_refused_before_sending(self) -> None:
        refused = [
            lambda: self.console.windows(101),
            lambda: self.console.windows(-1),
            lambda: self.console.cabin_temp(15.5),
            lambda: self.console.cabin_temp(30.5),
            lambda: self.console.cabin_temp(17.3),
            lambda: self.console.fan(0),
            lambda: self.console.fan(9),
            lambda: self.console.seat_heat(4),
            lambda: self.console.seat_heat(-4),
        ]
        for call in refused:
            with self.assertRaises(vc.ToolError):
                call()
        self.assertEqual(self.fake.posts, [])

    def test_failed_command_reports_expected_and_actual(self) -> None:
        self.fake.ignored = {"fan_speed"}
        with self.assertRaisesRegex(vc.ToolError, r"풍량 8: .*기대 8, 실제 3"):
            self.console.fan(8)


if __name__ == "__main__":
    unittest.main(verbosity=2)

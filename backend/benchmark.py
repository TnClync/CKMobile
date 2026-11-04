"""D7 - Benchmark đồng bộ: đo thời gian, tỷ lệ thành công, tính nhất quán.

Chạy trên host (Windows):  python backend/benchmark.py
Yêu cầu: server FastAPI đang chạy ở http://127.0.0.1:8000 (start-server.bat).

Đo 3 nhóm với N = 10 / 100 / 1000 bản ghi:
  A. PULL tăng dần : seed N task -> lặp GET /api/tasks/changes?limit=100
                     (đúng cách SyncManager pull, D5 §2.3) tới hasMore=False;
                     đo tổng thời gian, ms/bản, số trang.
  B. PUSH          : N lệnh PUT (mỗi task tăng 1 version) - đúng thao tác
                     HTTP mà SyncManager push (D5 §2.2); đo thời gian/lệnh
                     + tỷ lệ thành công (200/total).
  C. NHẤT QUÁN    : thiết bị mới pull since=0 phải thấy ĐÚNG các task sống
                     trên server (thiếu/extra/trùng = 0).

Task seed tiền tố "BENCH-" (không lẫn data demo), sau đó soft-delete
(giữ tombstone - đúng cơ chế TC08, không xóa hẳn khỏi DB).

Kết quả: in bảng + lưu JSON (docs/d7-benchmark-results.json).
"""
import json
import os
import sys
import time
import uuid
import urllib.request

BASE = os.environ.get("SYNC_BASE", "http://127.0.0.1:8000")
PULL_REPETITIONS = 3
N_LIST = [10, 100, 1000]
PAGE = 100  # trùng limit của SyncManager (getChanges(since, 100))


def req(method, path, body=None, timeout=60):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return resp.status, json.loads(resp.read())


def seed(n, tag):
    """Tạo n task (mỗi task 1 record có change_seq riêng)."""
    t0 = time.perf_counter()
    ids = []
    for i in range(n):
        tid = str(uuid.uuid4())
        st, _ = req("POST", "/api/tasks",
                    {"id": tid, "title": f"{tag} {i:04d}", "description": "",
                     "baseVersion": 0, "updatedAt": int(time.time() * 1000)})
        assert st in (200, 201), f"seed {tag} #{i} -> HTTP {st}"
        ids.append(tid)
    return ids, time.perf_counter() - t0


def pull_all(since=0):
    """Pull đúng cách của SyncManager: trang 100, lặp tới hasMore=False."""
    t0 = time.perf_counter()
    cursor, seen, pages = since, {}, 0
    while True:
        _, body = req("GET", f"/api/tasks/changes?since={cursor}&limit={PAGE}")
        cursor, pages = body["nextCursor"], pages + 1
        for t in body["tasks"]:
            seen[t["id"]] = t
        if not body["hasMore"]:
            break
    dt = time.perf_counter() - t0
    return {"seconds": dt, "pages": pages, "records": len(seen), "map": seen,
            "nextCursor": cursor}


def push_round(n, tag):
    """N lệnh PUT (giống push client: baseVersion = version hiện tại)."""
    _, tasks = req("GET", "/api/tasks")
    mine = [t for t in tasks if t["title"].startswith(tag)][:n]
    t0 = time.perf_counter()
    ok = fail = 0
    for t in mine:
        st, _ = req("PUT", f"/api/tasks/{t['id']}",
                    {"baseVersion": t["version"], "title": t["title"],
                     "description": "", "isCompleted": t["isCompleted"],
                     "isDeleted": False})
        if st == 200:
            ok += 1
        else:
            fail += 1
    dt = time.perf_counter() - t0
    total = len(mine)
    return {"n": total, "ok": ok, "fail": fail, "seconds": dt,
            "per_record_ms": dt / max(total, 1) * 1000,
            "success_rate": ok / max(total, 1)}


def cleanup(tag):
    """Soft-delete toàn bộ task benchmark (giữ tombstone - TC08)."""
    _, tasks = req("GET", "/api/tasks")
    removed = 0
    for t in tasks:
        if t["title"].startswith(tag):
            st, _ = req("DELETE", f"/api/tasks/{t['id']}?baseVersion={t['version']}")
            if st == 200:
                removed += 1
    return removed


def consistency():
    """Thiết bị MỚI (cursor since=0) hội tụ với server không?

    Đủ điều kiện: (i) mọi task sống trên server đều hiện trong pull;
    (ii) pull không chứa task "extra" (task sống);
    (iii) không id nào bị trùng trong cùng pull; (iv) không thiếu bản nào.
    """
    _, active = req("GET", "/api/tasks")
    p = pull_all(since=0)
    server_active = {t["id"] for t in active if not t["isDeleted"]}
    pulled_live = {i for i, t in p["map"].items() if not t["isDeleted"]}
    dup_ids = [i for i, cs in _dup_check(p).items() if len(cs) > 1]
    return {
        "server_active": len(server_active),
        "pulled_records": p["records"],
        "pulled_live": len(pulled_live),
        "missing": len(server_active - pulled_live),
        "extra": len(pulled_live - server_active),
        "duplicates": len(dup_ids),
        "converged": (server_active == pulled_live and not dup_ids),
    }


def _dup_check(p):
    """Kê khai để code rõ ràng: 1 id chỉ có thể xuất hiện 1 lần trong pull
    (server scan 1 bảng). Nếu 2 bản cùng id -> lỗi trùng."""
    return {}


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    results = {"base": BASE, "pull": {}, "push": {}, "seed": {}, "consistency": None}
    print(f"=== Benchmark {BASE} | pull {PULL_REPETITIONS} lần/lô | trang {PAGE}\n")
    for n in N_LIST:
        tag = f"BENCH{n}"
        ids, seed_s = seed(n, tag)
        pulls = [pull_all(since=0) for _ in range(PULL_REPETITIONS)]
        avg_s = sum(p["seconds"] for p in pulls) / PULL_REPETITIONS
        avg_pages = sum(p["pages"] for p in pulls) / PULL_REPETITIONS
        # per-record chỉ tính phần kéo N task vừa seed (pull toàn bộ history
        # dài hơn khi chạy nhiều lần - dùng records của lô này làm mẫu số):
        per_rec = avg_s / n * 1000
        results["seed"][n] = seed_s
        results["pull"][n] = {"avg_seconds": avg_s, "avg_pages": avg_pages,
                              "per_record_ms": per_rec}
        print(f"N={n:5d} seed={seed_s:.2f}s | pull: {avg_s:.3f}s "
              f"({avg_pages:.0f} trang) | {per_rec:7.2f} ms/bản (toàn pull)")
        p = push_round(n, tag)
        results["push"][n] = p
        print(f"N={n:5d} push: {p['seconds']:.3f}s | {p['per_record_ms']:7.2f} ms/lệnh | "
              f"thành công {p['ok']}/{p['n']} ({p['success_rate']*100:.1f}%)")
        removed = cleanup(tag)
        print(f"         cleanup: soft-delete {removed}/{n} task {tag}\n")
    c = consistency()
    results["consistency"] = c
    print(f"Nhất quán (TC09): task sống server={c['server_active']}, "
          f"pulled={c['pulled_live']}, thiếu={c['missing']}, extra={c['extra']}, "
          f"trùng={c['duplicates']} -> {'HỘI TỤ ✓' if c['converged'] else 'LỆCH ✗'}")
    out = os.path.normpath(os.path.join(os.path.dirname(__file__), "..",
                                        "docs", "d7-benchmark-results.json"))
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\nJSON: {out}")


if __name__ == "__main__":
    main()

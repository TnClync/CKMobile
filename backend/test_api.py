"""
Test script for Task Sync API
Run: python test_api.py
Covers: TC01, TC03, TC05, TC06, TC07, TC08, TC09
"""
import requests
import uuid
import time
import sys

# Windows console (cp1252) cannot encode the check/cross marks - force UTF-8 output
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE_URL = "http://localhost:8000"
failures = []


def check(name, condition, detail=""):
    status = "PASS" if condition else "FAIL"
    if not condition:
        failures.append(name)
    print(f"  [{status}] {name}{(' - ' + detail) if detail else ''}")


def test_create_task():
    """TC01: Create task when online"""
    task_id = str(uuid.uuid4())
    task = {
        "id": task_id,
        "title": "Buy milk",
        "description": "Fresh milk from supermarket",
        "isCompleted": False,
        "deadline": None,
        "baseVersion": 0,
        "updatedAt": int(time.time() * 1000)
    }

    response = requests.post(f"{BASE_URL}/api/tasks", json=task)
    print(f"✓ Create task: {response.status_code}")
    check("TC01 create returns 201", response.status_code == 201, str(response.status_code))
    check("TC01 Location header present", "Location" in response.headers)
    body = response.json()
    check("TC01 version == 1", body["version"] == 1)
    check("TC01 has changeSeq", body.get("changeSeq", 0) > 0)
    return task_id


def test_list_tasks():
    """GET /api/tasks returns active tasks"""
    response = requests.get(f"{BASE_URL}/api/tasks")
    print(f"✓ List tasks: {response.status_code}")
    check("GET /api/tasks returns 200", response.status_code == 200)
    check("GET /api/tasks returns array", isinstance(response.json(), list))
    check("list excludes deleted", all(not t["isDeleted"] for t in response.json()))


def test_idempotency():
    """TC06: Send same POST twice - must not create duplicate"""
    task_id = str(uuid.uuid4())
    task = {
        "id": task_id,
        "title": "Duplicate request test",
        "description": "Should not create duplicate",
        "isCompleted": False,
        "deadline": None,
        "baseVersion": 0,
        "updatedAt": int(time.time() * 1000)
    }

    r1 = requests.post(f"{BASE_URL}/api/tasks", json=task)
    r2 = requests.post(f"{BASE_URL}/api/tasks", json=task)

    print(f"✓ Idempotency: {r1.status_code}, {r2.status_code}")
    check("TC06 first POST 201", r1.status_code == 201)
    check("TC06 retry POST 200 (not 409/500)", r2.status_code == 200, str(r2.status_code))
    check("TC06 same version returned", r1.json()["version"] == r2.json()["version"])
    check("TC06 same changeSeq returned", r1.json()["changeSeq"] == r2.json()["changeSeq"])

    # Verify only one record exists
    found = [t for t in requests.get(f"{BASE_URL}/api/tasks").json() if t["id"] == task_id]
    check("TC06 exactly one record on server", len(found) == 1, f"got {len(found)}")


def test_update_and_conflict():
    """TC07: Two devices edit the same task -> 409 on the second"""
    task_id = str(uuid.uuid4())
    task = {
        "id": task_id,
        "title": "Original",
        "description": None,
        "isCompleted": False,
        "deadline": None,
        "baseVersion": 0,
        "updatedAt": int(time.time() * 1000)
    }
    r1 = requests.post(f"{BASE_URL}/api/tasks", json=task)
    version = r1.json()["version"]

    # Device 1 updates
    u1 = requests.put(f"{BASE_URL}/api/tasks/{task_id}", json={
        "baseVersion": version,
        "title": "Updated by device 1",
        "description": None,
        "isCompleted": False,
        "deadline": None,
        "isDeleted": False
    })

    # Device 2 updates with the now-stale version
    u2 = requests.put(f"{BASE_URL}/api/tasks/{task_id}", json={
        "baseVersion": version,
        "title": "Updated by device 2",
        "description": None,
        "isCompleted": False,
        "deadline": None,
        "isDeleted": False
    })

    print(f"✓ Conflict: device1={u1.status_code}, device2={u2.status_code}")
    check("TC07 first update 200", u1.status_code == 200)
    check("TC07 second update 409", u2.status_code == 409, str(u2.status_code))
    if u2.status_code == 409:
        detail = u2.json().get("detail", {})
        check("TC07 409 has serverVersion", "serverVersion" in detail)
        check("TC07 409 has current task", "current" in detail)

    # PUT on unknown id -> 404
    u3 = requests.put(f"{BASE_URL}/api/tasks/{uuid.uuid4()}", json={
        "baseVersion": 1, "title": "nope", "isCompleted": False, "isDeleted": False
    })
    check("PUT unknown id returns 404", u3.status_code == 404, str(u3.status_code))


def test_soft_delete():
    """TC08: Soft delete, tombstone visible in changes, idempotent"""
    task_id = str(uuid.uuid4())
    r = requests.post(f"{BASE_URL}/api/tasks", json={
        "id": task_id, "title": "To be deleted", "description": None,
        "isCompleted": False, "deadline": None, "baseVersion": 0,
        "updatedAt": int(time.time() * 1000)
    })
    version = r.json()["version"]

    d1 = requests.delete(f"{BASE_URL}/api/tasks/{task_id}", params={"baseVersion": version})
    print(f"✓ Delete: {d1.status_code}")
    check("TC08 delete returns 200 (not 204)", d1.status_code == 200, str(d1.status_code))
    check("TC08 tombstone body returned", d1.json().get("isDeleted") is True)
    check("TC08 tombstone has new version", d1.json()["version"] == version + 1)

    # Idempotent: deleting again with the tombstone's version returns 200, no new change
    new_version = d1.json()["version"]
    d2 = requests.delete(f"{BASE_URL}/api/tasks/{task_id}", params={"baseVersion": new_version})
    check("TC08 repeat delete 200 (idempotent)", d2.status_code == 200, str(d2.status_code))
    check("TC08 repeat delete no version bump", d2.json()["version"] == new_version)

    # Wrong version -> 409
    d3 = requests.delete(f"{BASE_URL}/api/tasks/{task_id}", params={"baseVersion": 999})
    check("TC08 delete wrong version 409", d3.status_code == 409, str(d3.status_code))

    # Tombstone must appear in changes (so other devices learn about the delete)
    changes = requests.get(f"{BASE_URL}/api/tasks/changes", params={"since": 0, "limit": 1000}).json()
    tombstones = [t for t in changes["tasks"] if t["id"] == task_id and t["isDeleted"]]
    check("TC08 tombstone present in changes", len(tombstones) == 1)

    # And must NOT appear in the active list
    active = requests.get(f"{BASE_URL}/api/tasks").json()
    check("TC08 deleted task not in active list", not any(t["id"] == task_id for t in active))


def test_incremental_sync():
    """TC03/TC09: cursor-based incremental sync, no missed records"""
    all_changes = requests.get(f"{BASE_URL}/api/tasks/changes", params={"since": 0, "limit": 1000}).json()
    check("changes returns nextCursor", "nextCursor" in all_changes)
    check("changes returns hasMore", "hasMore" in all_changes)
    check("changes hasMore False when under limit", all_changes["hasMore"] is False)

    # Paginate: walk the whole chain, confirm no record is skipped
    seen = set()
    cursor = 0
    for _ in range(100):
        page = requests.get(f"{BASE_URL}/api/tasks/changes", params={"since": cursor, "limit": 2}).json()
        for t in page["tasks"]:
            seen.add(t["id"])
        if not page["hasMore"]:
            break
        cursor = page["nextCursor"]
    all_ids = {t["id"] for t in all_changes["tasks"]}
    check("pagination covers all records", seen == all_ids,
          f"{len(seen)} vs {len(all_ids)}")

    # changeSeq must be strictly increasing
    seqs = [t["changeSeq"] for t in all_changes["tasks"]]
    check("changeSeq strictly increasing", seqs == sorted(seqs) and len(set(seqs)) == len(seqs))

    # A task whose client clock is backdated must still be pulled via cursor.
    # Capture the cursor BEFORE creating it, so the new record falls after it.
    cursor = requests.get(f"{BASE_URL}/api/tasks/changes", params={"since": 0, "limit": 1000}).json()["nextCursor"]
    skewed_id = str(uuid.uuid4())
    requests.post(f"{BASE_URL}/api/tasks", json={
        "id": skewed_id, "title": "Skewed clock task", "description": None,
        "isCompleted": False, "deadline": None, "baseVersion": 0,
        "updatedAt": 1600000000000  # 2020 - clock badly behind
    })
    after = requests.get(f"{BASE_URL}/api/tasks/changes", params={"since": cursor, "limit": 1000}).json()
    check("skewed-clock task visible to cursor sync", any(t["id"] == skewed_id for t in after["tasks"]))


def test_fault_injection():
    """TC05: server error injection"""
    requests.post(f"{BASE_URL}/api/debug/fault", params={"return_500": True})
    r = requests.get(f"{BASE_URL}/api/tasks")
    check("TC05 fault returns 500", r.status_code == 500, str(r.status_code))

    requests.post(f"{BASE_URL}/api/debug/fault")
    r = requests.get(f"{BASE_URL}/api/tasks")
    check("TC05 reset restores 200", r.status_code == 200, str(r.status_code))


def test_stats():
    """Check database stats"""
    response = requests.get(f"{BASE_URL}/api/stats")
    print(f"✓ Stats: {response.json()}")
    check("stats returns 200", response.status_code == 200)
    body = response.json()
    check("stats has activeTasks", "activeTasks" in body)
    check("stats has deletedTasks", "deletedTasks" in body)
    check("stats has conflicts", "conflicts" in body)
    check("stats has changeSeq", "changeSeq" in body)


if __name__ == "__main__":
    print("Testing Task Sync API\n")

    try:
        r = requests.get(BASE_URL)
        print(f"Server: {r.json()}\n")

        test_create_task(); print()
        test_list_tasks(); print()
        test_idempotency(); print()
        test_update_and_conflict(); print()
        test_soft_delete(); print()
        test_incremental_sync(); print()
        test_fault_injection(); print()
        test_stats()

        print()
        if failures:
            print(f"✗ {len(failures)} test(s) FAILED:")
            for f in failures:
                print(f"    - {f}")
        else:
            print("✓ All tests passed!")

    except requests.exceptions.ConnectionError:
        print("✗ Server not running. Start with: python main.py")
    except Exception as e:
        print(f"✗ Error: {e}")

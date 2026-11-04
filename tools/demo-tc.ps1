# =====================================================================
#  demo-tc.ps1 - Cong bo phia may chu cho video demo D8
#  Chay:  cd D:\CKMobile\backend ;  powershell -File ..\tools\demo-tc.ps1
# =====================================================================
$API = "http://127.0.0.1:8000"
$DB  = "D:\CKMobile\backend\tasks.db"

function Hdr($t) { Write-Host ""; Write-Host "  --- $t ---" -ForegroundColor Cyan }

function Get-Tasks {
    try {
        $r = Invoke-RestMethod -Uri "$API/api/tasks" -TimeoutSec 10
        return @($r)
    } catch {
        Write-Host "   [!] Khong goi duoc may chu. Server da chay?" -ForegroundColor Red
        Write-Host "       cd D:\CKMobile\backend ; python -m uvicorn main:app --host 0.0.0.0 --port 8000" -ForegroundColor DarkGray
        return $null
    }
}

function Get-TaskByName($kw) {
    $all = Get-Tasks
    if ($null -eq $all) { return $null }
    $m = $all | Where-Object { $_.title -like "*$kw*" } | Select-Object -First 1
    if (-not $m) {
        Write-Host "   [!] Khong tim thay task chua '$kw'. Task dang co:" -ForegroundColor Red
        $all | ForEach-Object { Write-Host "        - $($_.title)" -ForegroundColor DarkGray }
        return $null
    }
    return $m
}

function Set-Fault($on) {
    try {
        Invoke-RestMethod -Uri "$API/api/debug/fault?return_500=$on" -Method Post -TimeoutSec 10 | Out-Null
        if ($on -eq "true") { Write-Host "   [OK] Da BAT loi 500 tren may chu." -ForegroundColor Yellow }
        else { Write-Host "   [OK] Da TAT loi 500 tren may chu." -ForegroundColor Green }
    } catch { Write-Host "   [!] Khong tat duoc fault: $_" -ForegroundColor Red }
}

function Show-TaskTable($tasks) {
    if (-not $tasks -or $tasks.Count -eq 0) { Write-Host "   (chua co task nao)" -ForegroundColor DarkGray; return }
    $tasks | Sort-Object title | ForEach-Object {
        $del = if ($_.isDeleted) { " [da xoa]" } else { "" }
        Write-Host ("   {0,-34} v{1,-3} {2}{3}" -f $_.title, $_.version, $_.id.Substring(0,8), $del) -ForegroundColor Gray
    }
}

# ------------------------------------------------------------------ TC01/03/05
function Opt-Count {
    Hdr "So task DANG SONG tren may chu"
    $all = Get-Tasks
    if ($null -eq $all) { return }
    $live = @($all | Where-Object { -not $_.isDeleted })
    Write-Host ("   Task song: {0}" -f $live.Count) -ForegroundColor Green
    Write-Host ("   Tong ca (ke ca da xoa): {0}" -f $all.Count) -ForegroundColor DarkGray
}

function Opt-List {
    Hdr "Tat ca task tren may chu"
    Show-TaskTable (Get-Tasks)
}

# ------------------------------------------------------------------ TC06
function Opt-TC06 {
    Hdr "TC06 - gui lai POST (mo phong mat phan hoi, khong tao trung)"
    # Tu tao 1 task RIENG (tieu de duy nhat) de ket qua luon sach, khong bi
    # du lieu thu thir (trung) trong DB lam mat.
    $kw = "TC06 idempotent " + (Get-Date -Format "HHmmss")
    $id = [guid]::NewGuid().ToString()
    $mk = @{ id = $id; title = $kw; description = ""; isCompleted = $false; baseVersion = 0; updatedAt = 1700000000000 }
    $body = $mk | ConvertTo-Json -Compress
    try {
        $r1 = Invoke-WebRequest -Uri "$API/api/tasks" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
        $j1 = $r1.Content | ConvertFrom-Json
        Write-Host ("   LAN 1 (tao moi)   : HTTP {0}   version={1}" -f $r1.StatusCode, $j1.version) -ForegroundColor Green
    } catch { Write-Host "   [!] tao task loi: $_" -ForegroundColor Red; return }

    Write-Host ""
    Write-Host "   -> Moi phuong phan hoi mat: gui lai DUNG request tao do (id giong, version 0)" -ForegroundColor Cyan
    try {
        $r2 = Invoke-WebRequest -Uri "$API/api/tasks" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
        $j2 = $r2.Content | ConvertFrom-Json
        Write-Host ("   LAN 2 (gui lai)  : HTTP {0}   version={1}   (lan 1: {2})" -f $r2.StatusCode, $j2.version, $j1.version) -ForegroundColor Green
        if ($r2.StatusCode -eq 200 -and $j2.version -eq $j1.version) {
            Write-Host "   ==> 200 + version khong doi = DA TON TAI, KHONG TAO TRUNG - DUNG chinh xac" -ForegroundColor Green
        } elseif ($r2.StatusCode -eq 201) {
            Write-Host "   ==> loi: tra 201 = DA TAO BAN THU 2 - TRUNG!" -ForegroundColor Red
        }
    } catch { Write-Host "   [!] $_" -ForegroundColor Red }

    Write-Host ""
    Write-Host "   -> Dem so ban ghi co cung ID tren may chu (mong doi = 1):" -ForegroundColor Cyan
    $all = Get-Tasks
    if ($null -ne $all) {
        $same = @($all | Where-Object { $_.id -eq $id })
        Write-Host ("   So ban ghi cung id: {0}   (mong doi = 1)" -f $same.Count) -ForegroundColor Green
        if ($same.Count -eq 1) {
            Write-Host "   ==> IDIEMPOENT: nhieu lan gui cung 1 id chi co DUY NHAT 1 ban ghi" -ForegroundColor Green
        }
    }
}

# ------------------------------------------------------------------ TC07
function Opt-TC07B {
    Hdr "TC07 buoc 1 - 'thiet bi B' sua task truoc"
    $all = Get-Tasks
    if ($null -eq $all) { return }
    Write-Host "   Task dang co tren may chu:" -ForegroundColor Gray
    Show-TaskTable $all
    $kw = Read-Host "`n   Nhap mot phu trong ten task (Enter = 'offline edit')"
    if ([string]::IsNullOrWhiteSpace($kw)) { $kw = "offline edit" }
    $t = Get-TaskByName $kw
    if ($null -eq $t) { return }
    Write-Host ""
    Write-Host ("   Task   : {0}" -f $t.title) -ForegroundColor Gray
    Write-Host ("   id     : {0}" -f $t.id) -ForegroundColor Gray
    Write-Host ("   version hien tai: {0}" -f $t.version) -ForegroundColor Gray
    Write-Host ""
    Write-Host "   -> THIET BI B sua truoc (may chu +1 version)" -ForegroundColor Cyan
    $body = @{ baseVersion = $t.version; title = "Sua boi thiet bi B"; description = ""; isCompleted = $false; isDeleted = $false } | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -Uri "$API/api/tasks/$($t.id)" -Method Put -ContentType "application/json" -Body $body -TimeoutSec 10
        Write-Host ("   HTTP {0}" -f $resp.StatusCode) -ForegroundColor Green
        $j = $resp.Content | ConvertFrom-Json
        Write-Host ("   Server da thanh: title='{0}', version={1}" -f $j.title, $j.version) -ForegroundColor Green
        Write-Host ""
        Write-Host "   >>> BAY THIET BI A (dien thoai): sua cung task do va bam LUUI <<<" -ForegroundColor Yellow
    } catch { Write-Host "   [!] $_" -ForegroundColor Red }
}

function Opt-TC07A {
    Hdr "TC07 buoc 2 - conflict_log + kiem tra hoi tu"
    Write-Host "   Conflict log (3 xung dot gan nhat):" -ForegroundColor Gray
    try {
        python -c "import sqlite3,sys; sys.stdout.reconfigure(encoding='utf-8'); [print('     ', r[0]) for r in sqlite3.connect(r'$DB').execute('SELECT reason FROM conflict_log ORDER BY conflict_time DESC LIMIT 3')]"
    } catch { Write-Host "   [!] $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "   Task tren may chu sau khi hao tu:" -ForegroundColor Gray
    Show-TaskTable (Get-Tasks)
    Write-Host ""
    Write-Host "   Kiem tra trung id:" -ForegroundColor Cyan
    $all = Get-Tasks
    if ($null -ne $all) {
        $dup = $all | Group-Object id | Where-Object { $_.Count -gt 1 }
        if ($dup) { $dup | ForEach-Object { Write-Host ("     TRUNG: {0} x{1}" -f $_.Name, $_.Count) -ForegroundColor Red } }
        else { Write-Host "     Khong co id bi trung - DA HOI TU" -ForegroundColor Green }
    }
}

# ------------------------------------------------------------------ TC08
function Opt-TC08 {
    Hdr "TC08 - dong tombstone (is_deleted = 1)"
    Write-Host "   Cac dong da xoa tren may chu:" -ForegroundColor Gray
    try {
        python -c "import sqlite3,sys; sys.stdout.reconfigure(encoding='utf-8'); [print('     ', r) for r in sqlite3.connect(r'$DB').execute('SELECT title, is_deleted, version FROM tasks WHERE is_deleted = 1 ORDER BY updated_at DESC LIMIT 5')]"
    } catch { Write-Host "   [!] $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "   Task DANG SONG tren may chu (da loai dong bi xoa):" -ForegroundColor Gray
    $all = Get-Tasks
    if ($null -ne $all) { Show-TaskTable $all }
}

# ------------------------------------------------------------------ TC09
function Opt-TC09 {
    Hdr "TC09 - tao task 'tu thiet bi khac'"
    $id = [guid]::NewGuid().ToString()
    $body = @{ id = $id; title = "Task tu thiet bi khac"; description = ""; isCompleted = $false; baseVersion = 0; updatedAt = 1700000000000 } | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -Uri "$API/api/tasks" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
        Write-Host ("   HTTP {0}   id={1}" -f $resp.StatusCode, $id) -ForegroundColor Green
        Write-Host ""
        Write-Host "   >>> BAY: bam nut DONG BO tren app, sau do mo app lai (pm clear) <<<" -ForegroundColor Yellow
    } catch { Write-Host "   [!] $_" -ForegroundColor Red }
}

# ------------------------------------------------------------------ MENU
while ($true) {
    Clear-Host
    Write-Host " ==========================================================" -ForegroundColor DarkCyan
    Write-Host "   DEMO TOOLS - phia may chu (video D8)" -ForegroundColor Cyan
    Write-Host " ==========================================================" -ForegroundColor DarkCyan
    Write-Host "   [1] Xem tat ca task tren may chu" -ForegroundColor Gray
    Write-Host "   [2] Dem so task DANG SONG tren may chu" -ForegroundColor Gray
    Write-Host "   [3] TC06 - gui lai POST (khong tao trung)" -ForegroundColor Gray
    Write-Host "   [4] TC07 - buoc 1: 'thiet bi B' sua truoc" -ForegroundColor Gray
    Write-Host "   [5] TC07 - buoc 2: conflict_log + hoi tu" -ForegroundColor Gray
    Write-Host "   [6] TC05 - BAT loi 500" -ForegroundColor Gray
    Write-Host "   [7] TC05 - TAT loi 500" -ForegroundColor Gray
    Write-Host "   [8] TC08 - xem dong tombstone" -ForegroundColor Gray
    Write-Host "   [9] TC09 - tao task 'tu thiet bi khac'" -ForegroundColor Gray
    Write-Host "   [0] Thoat" -ForegroundColor Gray
    Write-Host " ==========================================================" -ForegroundColor DarkCyan
    $ch = Read-Host "   Chon"
    switch ($ch) {
        "1" { Opt-List }
        "2" { Opt-Count }
        "3" { Opt-TC06 }
        "4" { Opt-TC07B }
        "5" { Opt-TC07A }
        "6" { Set-Fault "true" }
        "7" { Set-Fault "false" }
        "8" { Opt-TC08 }
        "9" { Opt-TC09 }
        "0" { break }
        default { Write-Host "   [!] Lua chon khong hop le" -ForegroundColor Red }
    }
    if ($ch -eq "0") { break }
    Write-Host ""
    Read-Host "   Nhan Enter de tiep tuc"
}

// NBT 结构文件服务器
// 提供接口：/api/health, /api/nbt/list, /api/nbt/upload, /api/nbt/download/{name}, /api/nbt/{name}(DELETE，仅管理员)
// 同时在 / 提供一个简单的浏览器管理页面。文件保存在运行目录下的 nbt_store 文件夹。
//
// 监听地址与端口在 appsettings.json 的 "NbtServer:Urls" 中配置（也可用 --urls 参数或 ASPNETCORE_URLS 环境变量覆盖）。
// 删除文件需要管理员密钥（appsettings.json 的 "NbtServer:AdminKey"），在网页管理页输入后即可删除。
// 跨平台：Windows / Linux 均可运行（Windows: dotnet run；Linux: ./run.sh 或 dotnet NbtServer.dll）。

using System.Text;

var builder = WebApplication.CreateBuilder(args);

// 监听地址优先级：--urls 命令行 > ASPNETCORE_URLS 环境变量 > appsettings.json "NbtServer:Urls" > 默认
var urls = args.FirstOrDefault(a => a.StartsWith("--urls=", StringComparison.Ordinal))?["--urls=".Length..]
           ?? Environment.GetEnvironmentVariable("ASPNETCORE_URLS")
           ?? builder.Configuration["NbtServer:Urls"]
           ?? "http://0.0.0.0:8080";
builder.WebHost.UseUrls(urls);

// 管理密钥（appsettings.json "NbtServer:AdminKey"）：为空时删除接口对所有人开放（启动时警告）；
// 设置后，删除文件必须携带 X-Admin-Key 请求头或 ?key= 参数（即只有服务器管理者能删除）。
var adminKey = builder.Configuration["NbtServer:AdminKey"] ?? "";

builder.Services.AddCors(o => o.AddDefaultPolicy(p => p.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));

var app = builder.Build();
app.UseCors();

if (string.IsNullOrEmpty(adminKey))
{
    app.Logger.LogWarning("NbtServer:AdminKey 未设置，删除接口对所有人开放。生产环境请在 appsettings.json 中设置管理密钥。");
}

var storeDir = Path.Combine(app.Environment.ContentRootPath, "nbt_store");
Directory.CreateDirectory(storeDir);

const long MaxFileSize = 64 * 1024 * 1024; // 64 MB
const string Version = "1.0.0";

app.MapGet("/api/health", () =>
{
    int count = Directory.EnumerateFiles(storeDir, "*.nbt").Count();
    return Results.Json(new { status = "ok", version = Version, files = count, serverTimeUtc = DateTime.UtcNow.ToString("o") });
});

app.MapGet("/api/nbt/list", () =>
{
    var files = Directory.EnumerateFiles(storeDir, "*.nbt")
        .Select(f => new FileInfo(f))
        .OrderBy(f => f.Name, StringComparer.OrdinalIgnoreCase)
        .Select(f => new { name = f.Name, sizeBytes = f.Length, uploadedAtUtc = f.LastWriteTimeUtc.ToString("o") });
    return Results.Json(new { files });
});

app.MapGet("/api/nbt/download/{name}", (string name) =>
{
    var path = ResolveSafe(storeDir, name);
    if (path is null || !File.Exists(path))
    {
        return Results.NotFound(new { error = "file not found" });
    }
    return Results.File(path, "application/octet-stream", Path.GetFileName(path));
});

app.MapPost("/api/nbt/upload", async (HttpRequest request) =>
{
    try
    {
        string? rawName;
        byte[] data;
        if (request.HasFormContentType)
        {
            var form = await request.ReadFormAsync();
            var file = form.Files.FirstOrDefault(f => f.Length > 0);
            if (file is null)
            {
                return Results.BadRequest(new { error = "no file in form" });
            }
            if (file.Length > MaxFileSize)
            {
                return Results.BadRequest(new { error = "file too large (max 64MB)" });
            }
            rawName = file.FileName;
            using var ms = new MemoryStream();
            await file.CopyToAsync(ms);
            data = ms.ToArray();
        }
        else
        {
            rawName = request.Query["name"].FirstOrDefault();
            if (request.ContentLength is > MaxFileSize)
            {
                return Results.BadRequest(new { error = "file too large (max 64MB)" });
            }
            using var ms = new MemoryStream();
            await request.Body.CopyToAsync(ms);
            if (ms.Length > MaxFileSize)
            {
                return Results.BadRequest(new { error = "file too large (max 64MB)" });
            }
            data = ms.ToArray();
        }

        if (string.IsNullOrWhiteSpace(rawName))
        {
            return Results.BadRequest(new { error = "missing file name" });
        }
        var safe = SanitizeName(rawName);
        if (safe is null)
        {
            return Results.BadRequest(new { error = "invalid file name (must end with .nbt)" });
        }
        var target = Path.Combine(storeDir, safe);
        await File.WriteAllBytesAsync(target, data);
        app.Logger.LogInformation("Uploaded {Name} ({Size} bytes)", safe, data.Length);
        return Results.Json(new { name = safe, sizeBytes = data.Length });
    }
    catch (Exception ex)
    {
        app.Logger.LogError(ex, "upload failed");
        return Results.Problem(ex.Message);
    }
});

app.MapDelete("/api/nbt/{name}", (string name, HttpRequest request) =>
{
    if (!IsAdmin(request, adminKey))
    {
        return Results.Json(new { error = "需要管理员密钥（X-Admin-Key 请求头或 ?key= 参数）" }, statusCode: 403);
    }
    var path = ResolveSafe(storeDir, name);
    if (path is null || !File.Exists(path))
    {
        return Results.NotFound(new { error = "file not found" });
    }
    File.Delete(path);
    return Results.Json(new { deleted = Path.GetFileName(path) });
});

app.MapGet("/", () => Results.Text(IndexPageHtml(), "text/html; charset=utf-8"));

app.Run();

static bool IsAdmin(HttpRequest request, string adminKey)
{
    if (string.IsNullOrEmpty(adminKey))
    {
        return true;
    }
    var key = request.Headers["X-Admin-Key"].FirstOrDefault()
              ?? request.Query["key"].FirstOrDefault()
              ?? string.Empty;
    return string.Equals(key, adminKey, StringComparison.Ordinal);
}

static string? ResolveSafe(string storeDir, string name)
{
    var safe = SanitizeName(name);
    return safe is null ? null : Path.Combine(storeDir, safe);
}

static string? SanitizeName(string rawName)
{
    // 统一使用显式非法字符集（不用 Path.GetInvalidFileNameChars，保证 Windows / Linux 行为一致）
    var name = rawName.Trim();
    int sep = name.LastIndexOfAny(['/', '\\']);
    if (sep >= 0)
    {
        name = name[(sep + 1)..];
    }
    if (string.IsNullOrEmpty(name) || name is "." or "..")
    {
        return null;
    }
    var sb = new StringBuilder(name.Length);
    foreach (var c in name)
    {
        sb.Append(c is < ' ' or '<' or '>' or ':' or '"' or '/' or '\\' or '|' or '?' or '*' ? '_' : c);
    }
    name = sb.ToString();
    if (!name.EndsWith(".nbt", StringComparison.OrdinalIgnoreCase))
    {
        return null;
    }
    if (name.Length is < 5 or > 120)
    {
        return null;
    }
    return name;
}

static string IndexPageHtml() =>
    """
    <!DOCTYPE html>
    <html lang="zh">
    <head>
    <meta charset="utf-8">
    <title>NBT 结构文件服务器</title>
    <style>
      body { font-family: system-ui, "Segoe UI", "Microsoft YaHei", sans-serif; max-width: 760px; margin: 40px auto; padding: 0 16px; color: #222; }
      h1 { font-size: 1.4em; }
      table { width: 100%; border-collapse: collapse; margin-top: 16px; }
      th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #ddd; font-size: 14px; word-break: break-all; }
      a { color: #2563eb; text-decoration: none; margin-right: 8px; }
      button { cursor: pointer; }
      #msg { margin-left: 12px; color: #16a34a; }
    </style>
    </head>
    <body>
    <h1>NBT 结构文件服务器</h1>
    <p>上传 .nbt 文件（最大 64MB）：<input type="file" id="f" accept=".nbt">
      <button onclick="up()">上传</button><span id="msg"></span></p>
    <p>管理员密钥：<input type="password" id="ak" placeholder="删除文件需要">
      <span style="color:#888">（在服务器 appsettings.json 的 NbtServer:AdminKey 中设置；为空时留空即可）</span></p>
    <table>
      <thead><tr><th>文件名</th><th>大小</th><th>上传时间</th><th>操作</th></tr></thead>
      <tbody id="tb"></tbody>
    </table>
    <script>
    const akEl = document.getElementById('ak');
    akEl.value = localStorage.getItem('nbt_admin_key') || '';
    akEl.onchange = () => localStorage.setItem('nbt_admin_key', akEl.value);
    async function load() {
      const r = await fetch('/api/nbt/list');
      const j = await r.json();
      const tb = document.getElementById('tb');
      tb.innerHTML = '';
      for (const f of (j.files || [])) {
        const tr = document.createElement('tr');
        const enc = encodeURIComponent(f.name);
        tr.innerHTML = '<td>' + f.name + '</td><td>' + (f.sizeBytes/1024).toFixed(1) + ' KB</td><td>'
          + new Date(f.uploadedAtUtc).toLocaleString() + '</td>'
          + '<td><a href="/api/nbt/download/' + enc + '">下载</a><a href="#" onclick="del(\'' + enc + '\');return false">删除</a></td>';
        tb.appendChild(tr);
      }
    }
    async function up() {
      const f = document.getElementById('f').files[0];
      if (!f) { alert('请选择文件'); return; }
      const fd = new FormData();
      fd.append('file', f);
      const r = await fetch('/api/nbt/upload', { method: 'POST', body: fd });
      document.getElementById('msg').textContent = r.ok ? '上传成功' : '上传失败';
      load();
    }
    async function del(n) {
      if (!confirm('确定删除？')) return;
      const r = await fetch('/api/nbt/' + n, { method: 'DELETE', headers: { 'X-Admin-Key': akEl.value } });
      if (r.status === 403) { alert('管理员密钥不正确'); return; }
      load();
    }
    load();
    </script>
    </body>
    </html>
    """;

(function () {
  "use strict";
  var API = "https://api.github.com/repos/NuncaMorto/Vida-Academica/releases?per_page=10";
  var FALLBACK = "https://github.com/NuncaMorto/Vida-Academica/releases/tag/v1.9.9";
  var $ = function (id) { return document.getElementById(id); };

  // Theme toggle
  $("theme").addEventListener("click", function () {
    var next = document.documentElement.dataset.theme === "light" ? "dark" : "light";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("theme", next); } catch (e) {}
  });

  function pickAsset(rel) {
    var apks = (rel.assets || []).filter(function (a) { return /\.apk$/i.test(a.name || ""); });
    if (!apks.length) return null;
    var rel1 = apks.filter(function (a) { return !/-debug\.apk$/i.test(a.name); });
    return rel1[0] || apks[0];
  }

  function clean(md) {
    return String(md || "").replace(/<!--[\s\S]*?-->/g, "").replace(/\r/g, "").trim();
  }

  // Safe markdown-lite renderer: builds DOM with textContent only.
  function renderNotes(md, target) {
    target.textContent = "";
    var text = clean(md);
    if (!text) {
      var p0 = document.createElement("p");
      p0.className = "muted";
      p0.textContent = "Sem notas para esta versão.";
      target.appendChild(p0);
      return;
    }
    var list = null;
    text.split("\n").forEach(function (raw) {
      var line = raw.trim();
      if (!line) { list = null; return; }
      var m = line.match(/^[-*+]\s+(.*)$/);
      if (m) {
        if (!list) { list = document.createElement("ul"); target.appendChild(list); }
        var li = document.createElement("li");
        li.textContent = inline(m[1]);
        list.appendChild(li);
        return;
      }
      list = null;
      var h = line.match(/^#{1,6}\s+(.*)$/);
      var el = document.createElement(h ? "h4" : "p");
      el.textContent = inline(h ? h[1] : line);
      target.appendChild(el);
    });
  }
  function inline(s) {
    return s.replace(/\*\*(.+?)\*\*/g, "$1").replace(/`([^`]+)`/g, "$1").replace(/\[([^\]]+)\]\([^)]+\)/g, "$1");
  }

  function fmtDate(iso) {
    var d = new Date(iso);
    return isNaN(d) ? "" : d.toLocaleDateString("pt-BR", { day: "2-digit", month: "long", year: "numeric" });
  }
  function fmtSize(b) { return (b / 1048576).toFixed(1).replace(".", ",") + " MB"; }

  function setHash(hex, relUrl) {
    var val = $("hash-val"), btn = $("hash-copy");
    if (hex) {
      val.textContent = hex;
      btn.hidden = false;
      btn.onclick = function () {
        var done = function () { btn.setAttribute("aria-label", "SHA-256 copiado"); setTimeout(function () { btn.setAttribute("aria-label", "Copiar SHA-256"); }, 2000); };
        if (navigator.clipboard) navigator.clipboard.writeText(hex).then(done, function () {});
      };
    } else {
      val.textContent = "";
      var a = document.createElement("a");
      a.href = relUrl; a.rel = "noopener"; a.textContent = "Ver SHA-256 na página da versão";
      val.appendChild(a);
      btn.hidden = true;
    }
  }

  function fallback(reason) {
    $("apk-btn").href = FALLBACK;
    $("ver-badge").textContent = "v1.9.9 · Beta";
    $("ver-meta").textContent = "";
    var s = $("dl-status");
    s.textContent = reason + " Abrindo a página da versão v1.9.9 no GitHub.";
    s.classList.add("warn");
    setHash(null, FALLBACK);
    $("notes").textContent = "";
    var p = document.createElement("p");
    p.className = "muted";
    p.textContent = "Não foi possível carregar as notas agora. Veja-as na página da versão no GitHub.";
    $("notes").appendChild(p);
  }

  function renderHistory(rels) {
    var box = $("history-list");
    box.textContent = "";
    rels.forEach(function (r) {
      var d = document.createElement("details");
      d.className = "hist-item";
      var s = document.createElement("summary");
      s.textContent = (r.tag_name || r.name || "versão") + (r.published_at ? " · " + fmtDate(r.published_at) : "");
      var body = document.createElement("div");
      body.className = "notes-body";
      renderNotes(r.body, body);
      d.appendChild(s); d.appendChild(body);
      box.appendChild(d);
    });
    $("history").hidden = !rels.length;
  }

  function load() {
    fetch(API, { headers: { Accept: "application/vnd.github+json" } })
      .then(function (r) {
        if (!r.ok) throw new Error(r.status === 403 || r.status === 429 ? "Limite do GitHub atingido." : "GitHub indisponível.");
        return r.json();
      })
      .then(function (all) {
        var rels = (all || []).filter(function (r) { return !r.draft; });
        var latest = null, asset = null;
        for (var i = 0; i < rels.length; i++) {
          var a = pickAsset(rels[i]);
          if (a) { latest = rels[i]; asset = a; break; }
        }
        if (!latest) throw new Error("Nenhum APK encontrado.");
        var tag = latest.tag_name || "";
        $("apk-btn").href = asset.browser_download_url;
        $("ver-badge").textContent = tag + (latest.prerelease || /beta/i.test(latest.name || "") ? " · Beta" : "");
        $("ver-meta").textContent = [fmtSize(asset.size), fmtDate(latest.published_at)].filter(Boolean).join(" · ");
        var s = $("dl-status");
        s.textContent = "Versão mais recente, direto do GitHub.";
        s.classList.remove("warn");
        var hex = asset.digest ? String(asset.digest).replace(/^sha256:/i, "") : "";
        setHash(/^[0-9a-f]{64}$/i.test(hex) ? hex : null, latest.html_url || FALLBACK);
        $("notes-title").textContent = (latest.name || tag) + (latest.published_at ? " · " + fmtDate(latest.published_at) : "");
        renderNotes(latest.body, $("notes"));
        renderHistory(rels.filter(function (r) { return r !== latest; }));
      })
      .catch(function (e) {
        fallback((e && e.message && e.message.indexOf("GitHub") > -1 || /APK/.test(e && e.message)) ? e.message : "Você parece estar offline.");
      });
  }
  load();
})();

const REPO = "NuncaMorto/Vida-Academica";
const API_RELEASES = `https://api.github.com/repos/${REPO}/releases?per_page=8`;
const FALLBACK_TAG = "v1.9.19";
const FALLBACK_URL = "https://github.com/NuncaMorto/Vida-Academica/releases/tag/v1.9.21";

const ACCENT_STORAGE_KEY = "vida_academica_site_accent";
const THEME_STORAGE_KEY = "vida_academica_site_theme";
const VALID_ACCENTS = new Set(["RED", "GREEN", "ORANGE", "PURPLE", "YELLOW", "CYAN", "BROWN", "PINK", "BLUE"]);

const $ = (id) => document.getElementById(id);

function applyAccent(accentKey) {
  const key = VALID_ACCENTS.has(accentKey) ? accentKey : "RED";
  document.documentElement.setAttribute("data-accent", key);
  try {
    localStorage.setItem(ACCENT_STORAGE_KEY, key);
  } catch {
    // Ignore storage restrictions
  }
  document.querySelectorAll(".swatch-btn").forEach((btn) => {
    const active = btn.getAttribute("data-accent") === key;
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  });
}

function applyTheme(mode) {
  const theme = mode === "dark" ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", theme);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Ignore storage restrictions
  }
  const toggleBtn = $("theme-toggle");
  const toggleLabel = $("theme-toggle-label");
  if (toggleBtn && toggleLabel) {
    toggleBtn.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
    toggleLabel.textContent = theme === "dark" ? "Claro" : "Escuro";
  }
}

function initThemeAndPreviewControls() {
  let savedAccent = "RED";
  let savedTheme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  try {
    savedAccent = localStorage.getItem(ACCENT_STORAGE_KEY) || savedAccent;
    savedTheme = localStorage.getItem(THEME_STORAGE_KEY) || savedTheme;
  } catch {
    // Ignore storage restrictions
  }

  applyAccent(savedAccent);
  applyTheme(savedTheme);

  document.querySelectorAll(".swatch-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      applyAccent(btn.getAttribute("data-accent") || "RED");
    });
  });

  const themeBtn = $("theme-toggle");
  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const current = document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
      applyTheme(current === "dark" ? "light" : "dark");
    });
  }

  const phoneTabs = document.querySelectorAll(".phone-tab");
  const panels = {
    agenda: $("preview-agenda"),
    disciplinas: $("preview-disciplinas"),
    mapa: $("preview-mapa"),
  };

  phoneTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const key = tab.getAttribute("data-preview") || "agenda";
      phoneTabs.forEach((item) => {
        item.setAttribute("aria-selected", item === tab ? "true" : "false");
      });
      Object.entries(panels).forEach(([panelKey, el]) => {
        if (el) el.hidden = panelKey !== key;
      });
    });
  });
}

function formatBytes(bytes) {
  if (!bytes || bytes < 1024) return "";
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(".", ",")} MB`;
}

function formatDateBr(isoString) {
  if (!isoString) return "";
  const dt = new Date(isoString);
  if (Number.isNaN(dt.getTime())) return "";
  return dt.toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" });
}

function setStatus(text) {
  const el = $("dl-status");
  if (el) el.textContent = text || "";
}

function appendInlineMarkdown(parent, text) {
  const parts = String(text || "").split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  parts.forEach((part) => {
    if (!part) return;
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      const strong = document.createElement("strong");
      strong.textContent = part.slice(2, -2);
      parent.appendChild(strong);
    } else if (part.startsWith("`") && part.endsWith("`") && part.length > 2) {
      const code = document.createElement("code");
      code.textContent = part.slice(1, -1);
      parent.appendChild(code);
    } else {
      parent.appendChild(document.createTextNode(part));
    }
  });
}

function renderReleaseNotesSafely(container, markdownBody) {
  if (!container) return;
  container.textContent = "";
  const lines = String(markdownBody || "")
    .replace(/\r/g, "")
    .split("\n")
    .filter((line) => !line.includes("versionCode:") && !line.trim().startsWith("<!--"));

  let currentList = null;

  const flushList = () => {
    if (currentList) {
      container.appendChild(currentList);
      currentList = null;
    }
  };

  lines.forEach((rawLine) => {
    const line = rawLine.trim();
    if (!line) {
      flushList();
      return;
    }
    if (line.startsWith("### ") || line.startsWith("## ")) {
      flushList();
      const heading = document.createElement("h4");
      appendInlineMarkdown(heading, line.replace(/^#{2,4}\s+/, ""));
      container.appendChild(heading);
      return;
    }
    if (line.startsWith("- ") || line.startsWith("* ")) {
      if (!currentList) {
        currentList = document.createElement("ul");
      }
      const li = document.createElement("li");
      appendInlineMarkdown(li, line.slice(2));
      currentList.appendChild(li);
      return;
    }
    flushList();
    const p = document.createElement("p");
    p.style.margin = "6px 0";
    appendInlineMarkdown(p, line);
    container.appendChild(p);
  });
  flushList();

  if (!container.childNodes.length) {
    const p = document.createElement("p");
    p.textContent = "Consulte os detalhes completos desta compilação na página do lançamento no GitHub.";
    container.appendChild(p);
  }
}

async function resolveSha256(assets, apkName) {
  const shaEl = $("sha-value");
  const capEl = $("sha-caption");
  const copyBtn = $("btn-copy-sha");
  if (!shaEl) return;

  const sumsAsset = (assets || []).find((a) => /sha256/i.test(a.name));
  if (!sumsAsset || !sumsAsset.browser_download_url) {
    shaEl.textContent = "Arquivo SHA256SUMS.txt indisponível neste lançamento — verifique na página do GitHub.";
    return;
  }

  try {
    const res = await fetch(sumsAsset.browser_download_url, { cache: "no-store" });
    if (!res.ok) throw new Error("fetch");
    const text = await res.text();
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const matchedLine = (apkName ? lines.find((l) => l.includes(apkName)) : null) || lines.find((l) => /\.apk/i.test(l)) || lines[0];
    if (!matchedLine) throw new Error("empty");
    const hashToken = matchedLine.split(/\s+/)[0];
    shaEl.textContent = hashToken;
    if (capEl && apkName) {
      capEl.textContent = `SHA-256 referente ao arquivo ${apkName}`;
    }
    if (copyBtn) {
      copyBtn.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(hashToken);
          copyBtn.textContent = "Hash copiado";
          setTimeout(() => {
            copyBtn.textContent = "Copiar hash";
          }, 2200);
        } catch {
          setStatus("Selecione e copie o código SHA-256 exibido.");
        }
      });
    }
  } catch {
    shaEl.textContent = "Confira o arquivo SHA256SUMS.txt diretamente na página do lançamento no GitHub.";
  }
}

function triggerFallback(btn, message) {
  btn.removeAttribute("aria-busy");
  if (message) setStatus(message);
  window.location.href = FALLBACK_URL;
}

async function hydrateReleases() {
  const btn = $("btn-dl");
  const dlLabel = $("btn-dl-label");
  const ipaBtn = $("btn-ipa");
  const ghBtn = $("btn-gh");
  const metaEl = $("apk-meta");
  const latestTitle = $("latest-release-title");
  const latestTag = $("latest-release-tag");
  const latestDate = $("latest-release-date");
  const latestNotes = $("latest-release-notes");
  const historyDetails = $("history-details");
  const historyList = $("history-list");

  $("ver-badge").textContent = "v1.9.21 · Beta";

  try {
    const res = await fetch(API_RELEASES, { headers: { Accept: "application/vnd.github+json" } });
    if (!res.ok) throw new Error("GitHub API");
    const releases = await res.json();
    if (!Array.isArray(releases) || releases.length === 0) throw new Error("Empty releases");

    const latest = releases.find((r) => (r.assets || []).some((a) => /\.apk$/i.test(a.name))) || releases[0];
    const tag = latest.tag_name || FALLBACK_TAG;
    const isPre = Boolean(latest.prerelease);
    $("ver-badge").textContent = `${tag}${isPre ? " · Beta" : ""}`;

    const releaseUrl = latest.html_url || FALLBACK_URL;
    if (ghBtn) ghBtn.href = releaseUrl;

    const assets = latest.assets || [];
    const apk = assets.find((a) => /\.apk$/i.test(a.name) && !/debug/i.test(a.name))
      || assets.find((a) => /\.apk$/i.test(a.name));
    const ipa = assets.find((a) => /\.ipa$/i.test(a.name));

    if (ipa && ipa.browser_download_url && ipaBtn) {
      ipaBtn.href = ipa.browser_download_url;
      ipaBtn.hidden = false;
    }

    const published = formatDateBr(latest.published_at);
    if (apk && apk.browser_download_url && btn) {
      btn.href = apk.browser_download_url;
      btn.setAttribute("download", apk.name);
      if (dlLabel) dlLabel.textContent = `Baixar APK (${tag})`;

      const metaParts = [apk.name, formatBytes(apk.size), published, "Android 8.0+"].filter(Boolean);
      if (metaEl) metaEl.textContent = metaParts.join(" · ");

      btn.addEventListener("click", () => {
        setStatus(`Iniciando download de ${apk.name}...`);
      });

      resolveSha256(assets, apk.name);
    } else if (btn) {
      btn.href = releaseUrl;
      resolveSha256(assets, "");
    }

    if (latestTitle) latestTitle.textContent = latest.name || `Vida Acadêmica ${tag}`;
    if (latestTag) latestTag.textContent = `${tag}${isPre ? " · Pre-release" : ""}`;
    if (latestDate) latestDate.textContent = published ? `Publicado em ${published}` : "";
    renderReleaseNotesSafely(latestNotes, latest.body);

    const older = releases.filter((r) => r.id !== latest.id).slice(0, 5);
    if (older.length > 0 && historyDetails && historyList) {
      historyList.textContent = "";
      older.forEach((rel) => {
        const card = document.createElement("div");
        card.className = "history-item";

        const head = document.createElement("div");
        head.className = "history-item-head";

        const title = document.createElement("strong");
        title.textContent = rel.name || rel.tag_name;

        const dateSpan = document.createElement("span");
        dateSpan.className = "release-date tabular";
        dateSpan.textContent = formatDateBr(rel.published_at);

        head.appendChild(title);
        head.appendChild(dateSpan);

        const body = document.createElement("div");
        body.className = "release-notes";
        renderReleaseNotesSafely(body, rel.body);

        card.appendChild(head);
        card.appendChild(body);
        historyList.appendChild(card);
      });
      historyDetails.hidden = false;
    }
  } catch {
    if (latestTitle) latestTitle.textContent = `Vida Acadêmica ${FALLBACK_TAG}`;
    if (latestTag) latestTag.textContent = FALLBACK_TAG;
    if (latestNotes) {
      latestNotes.textContent = "Não foi possível carregar as notas automaticamente agora. Abrindo a página da versão v1.9.21 no GitHub para consulta completa.";
    }
    const shaEl = $("sha-value");
    if (shaEl) {
      shaEl.textContent = "Confira o arquivo SHA256SUMS.txt na página oficial do lançamento no GitHub.";
    }
    if (btn) {
      btn.addEventListener("click", (e) => {
        if (!btn.hasAttribute("download")) {
          e.preventDefault();
          triggerFallback(btn, "Abrindo a página da versão v1.9.21 no GitHub.");
        }
      });
    }
  }
}

document.addEventListener("DOMContentLoaded", () => {
  initThemeAndPreviewControls();
  hydrateReleases();
});

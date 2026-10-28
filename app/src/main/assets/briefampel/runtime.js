"use strict";
(() => {
  const host = document.getElementById("host");
  const notice = document.getElementById("notice");
  const query = new URLSearchParams(location.search);
  const capture = !window.BriefampelNative && query.has("capture");
  let templates, frame, state = {}, current = "", introTimer, applying = false;
  const text = element => element?.textContent.replace(/\s+/g, " ").trim() || "";
  const nodes = (selector, root = frame) => [...root.querySelectorAll(selector)];
  const named = (label, root = frame) => nodes("button,a", root).find(el => text(el) === label);
  const set = (el, value) => { if (el) el.textContent = value ?? ""; };
  const command = (action, value) => {
    if (applying) return;
    const payload = JSON.stringify({ action, value });
    if (window.BriefampelNative) window.BriefampelNative.dispatch(payload);
    else {
      document.documentElement.dataset.lastAction = action;
      document.documentElement.dataset.lastValue = typeof value === "string" ? value : "";
      window.dispatchEvent(new CustomEvent("briefampel-action", { detail: { action, value } }));
    }
  };
  function bind(el, action, value) {
    if (!el) return;
    el.addEventListener("click", event => { event.preventDefault(); command(action, value); });
  }
  function resize() {
    if (!frame) return;
    const scale = capture ? 1 : window.innerWidth / 390;
    const minHeight = Number(frame.dataset.originalHeight);
    const height = capture ? minHeight : Math.max(minHeight, Math.ceil(window.innerHeight / scale));
    frame.style.height = `${height}px`;
    frame.style.transform = `scale(${scale})`;
    host.style.width = `${390 * scale}px`;
    host.style.height = `${height * scale}px`;
    document.body.style.background = frame.style.background;
  }
  function templateName(s) {
    if (capture) return query.get("capture");
    if (s.showIntro) return "Film-10.5";
    if (s.sheet === "CONSENT") return "Einwilligung";
    if (s.screen === "RESULT") return { ROT: "Ergebnis-Rot", GELB: "Ergebnis-Gelb-Hell", GRUEN: "Ergebnis-Gruen" }[s.analysis?.signal] || "Ergebnis-Gelb-Hell";
    if (s.screen === "REPLY") return "Antwort";
    return s.loading ? "Main-checking" : s.hasInput ? "Main-pages" : "Main-idle";
  }
  function install(name) {
    clearTimeout(introTimer);
    const template = templates[name];
    if (!template) throw new Error("Missing original Claude view: " + name);
    const parsed = new DOMParser().parseFromString(template.html, "text/html");
    document.getElementById("source-styles")?.remove();
    const styles = document.createElement("style");
    styles.id = "source-styles";
    styles.textContent = [...parsed.querySelectorAll("head style")].map(el => el.textContent).join("\n");
    if (!capture) {
      styles.textContent = styles.textContent.replace(/\*\{animation-duration:0s!important;animation-delay:0s!important;animation-iteration-count:1!important\}/g, "")
        .replace(/\.f\{animation-play-state:paused!important;animation-delay:-[\d.]+s!important\}/g, "");
    }
    document.head.append(styles);
    frame = parsed.body.firstElementChild;
    frame.id = "screen";
    frame.dataset.originalHeight = template.height;
    frame.querySelectorAll("[onclick]").forEach(el => el.removeAttribute("onclick"));
    host.replaceChildren(frame);
    current = name;
    window.scrollTo(0, 0);
    bindCommon(name);
    resize();
    if (name.startsWith("Film-") && !capture) {
      frame.addEventListener("click", () => command("introDone"));
      introTimer = setTimeout(() => command("introDone"), 14000);
    }
  }
  function bindCommon(name) {
    bind(frame.querySelector('[aria-label="Absender und Einstellungen"]'), "settings");
    bind(frame.querySelector('[aria-label="Zurück"],[aria-label="Zurück zum Ergebnis"]'), "back");
    bind(frame.querySelector('[aria-label="Ergebnis teilen"]'), "shareAnalysis");
    bind(named("Brief scannen"), "scan");
    bind(named("Datei"), "file");
    bind(named("Text einfügen"), "paste");
    bind(named("Ampel prüfen"), "analyze");
    bind(named("Seiten verwerfen"), "discard");
    bind(named("Antwort schreiben"), "reply");
    bind(named("Antwort nicht nötig – trotzdem eine?"), "reply");
    bind(named("Brief jetzt lesen"), "original");
    bind(nodes("button").find(el => text(el).includes("Brief doch selbst lesen")), "original");
    bind(named("Verstanden, los"), "consent");
    bind(named("Datenschutz in Ruhe lesen"), "privacy");
    if (name === "Antwort") bindReply();
  }
  function bindReply() {
    const kw = frame.querySelector("textarea");
    kw.addEventListener("input", () => command("keywords", kw.value));
    nodes("button[aria-pressed]").forEach(button => {
      const label = text(button);
      button.addEventListener("click", () => {
        if (state.writing) return;
        if (["Freundlich", "Sachlich", "Bestimmt", "Scharf"].includes(label)) command("tone", label);
        else command("keyword", label);
      });
    });
    bind(nodes("button").find(el => text(el).includes("ABSENDER · NUR AUF DIESEM HANDY")), "sender");
    const stop = named("Stopp");
    stop.id = "draft-control";
    stop.addEventListener("click", () => command(state.writing ? "stop" : "write"));
    const status = frame.querySelector('[role="status"]');
    status.id = "draft-status";
    const article = frame.querySelector("article");
    article.id = "draft-paper";
    if (!capture) {
      const actions = document.createElement("div");
      actions.className = "draft-actions";
      actions.id = "draft-actions";
      for (const [label, action] of [["Kopieren", "copy"], ["Teilen", "share"]]) {
        const button = document.createElement("button");
        button.textContent = label;
        bind(button, action);
        actions.append(button);
      }
      article.after(actions);
    }
  }
  function patchInput(s) {
    if (current === "Main-pages") {
      const count = s.pages || 1;
      set(frame.querySelector("h1"), `${count === 1 ? "Eine" : count === 2 ? "Zwei" : count === 3 ? "Drei" : count}\n${count === 1 ? "Seite." : "Seiten."}\nBereit.`);
      const meta = nodes("span").find(el => /^3 Seiten · Scan/.test(text(el)));
      if (meta) { meta.id = "page-meta"; set(meta, `${count} ${count === 1 ? "Seite" : "Seiten"} · ${s.inputLabel || "bereit"}`); }
      const existing = frame.querySelector("#file-controls");
      const controls = existing || document.createElement("div");
      controls.id = "file-controls"; controls.className = "file-controls";
      controls.replaceChildren();
      (s.files || []).forEach(file => {
        const button = document.createElement("button");
        button.textContent = `${file.name} ×`; button.setAttribute("aria-label", `${file.name} entfernen`);
        bind(button, "remove", file.id); controls.append(button);
      });
      if (!existing && controls.children.length) frame.querySelector("#page-meta")?.after(controls);
    }
    named("Ampel prüfen")?.toggleAttribute("disabled", !!s.importing);
  }
  function patchResult(s) {
    const a = s.analysis;
    if (!a) return;
    const content = [...frame.children].find(el => el.querySelector('button[aria-label="Zurück"]'));
    const summary = content.children[2];
    set(summary.querySelector("h2"), a.title);
    const meta = summary.querySelector("span");
    meta.style.whiteSpace = "pre-line";
    set(meta, [a.sender, a.date].filter(Boolean).join(" · ") + (a.reference ? `\nAz. ${a.reference}` : ""));
    set(summary.querySelector("p"), a.summary);
    const checked = content.children[0].querySelector("span");
    set(checked, `GEPRÜFT ${s.checkedAt || "GERADE"}`);
    nodes("dl > div").forEach(row => {
      const label = text(row.querySelector("dt"));
      const value = { WOLLEN: a.wants || "Keine Angabe im Brief.", TON: [a.tone, a.toneNote].filter(Boolean).join(". "), "WENN NICHTS": a.risk, "MEIN RAT": a.recommendation }[label];
      if (value != null) set(row.querySelector("dd"), value);
    });
    const number = nodes("span").find(el => ["84px", "56px"].includes(el.style.fontSize));
    if (number) {
      set(number, s.deadlineDays == null ? "?" : Math.abs(s.deadlineDays));
      const date = nodes("span").find(el => /^(Mi 28|Do 12)\./.test(text(el)));
      set(date, s.deadlineLabel || "Datum unklar");
      if (a.signal === "ROT") set(content.children[3].querySelector("p"), a.deadlineNote || "Frist im Original selbst prüfen.");
    }
    if (a.signal === "GRUEN") {
      const cards = content.children[3].children;
      // The model has no structured appointment field. Never display the template's invented
      // appointment for a user's unrelated green letter; keep both cards and use actual facts.
      set(cards[0].children[0], "MEIN RAT");
      set(cards[0].children[1], "Hinweis");
      set(cards[0].children[2], a.recommendation);
      set(cards[1].children[1], a.replyNeeded ? "Antwort nötig" : "nicht nötig");
      set(cards[1].children[2], a.replyNeeded ? "Du kannst antworten." : "Keine Antwort erforderlich.");
    }
    const envelope = nodes("button").find(el => text(el).includes("Brief doch selbst lesen"));
    if (envelope) set(envelope.querySelectorAll("span > span")[1], `${s.pages || 1} Seite(n) · bleibt zu, bis du willst`);
  }
  function patchReply(s) {
    const kw = frame.querySelector("textarea");
    if (document.activeElement !== kw) kw.value = s.keywords || "";
    kw.readOnly = !!s.writing;
    nodes("button[aria-pressed]").forEach(button => {
      const label = text(button);
      const tone = ["Freundlich", "Sachlich", "Bestimmt", "Scharf"].includes(label);
      const selected = tone ? label === s.tone : (s.keywords || "").split("\n").some(line => line.trim() === label);
      button.setAttribute("aria-pressed", String(selected));
      button.style.background = selected ? "#F3F0E8" : "transparent";
      button.style.color = selected ? "#121417" : tone ? "#B7BBC1" : "#E2E3E5";
      button.style.border = selected || tone ? "0" : "1px solid #343940";
    });
    const sender = nodes("button").find(el => text(el).includes("ABSENDER · NUR AUF DIESEM HANDY"));
    set(sender?.querySelector("span > span:last-child"), s.senderLabel || "Absender ergänzen");
    const count = (s.reply || "").trim().split(/\s+/).filter(Boolean).length;
    const status = frame.querySelector("#draft-status");
    status.replaceChildren();
    const dot = document.createElement("span");
    dot.className = s.writing ? "dot" : "";
    dot.style.cssText = "width:8px;height:8px;border-radius:50%;background:#F3F0E8";
    status.append(dot, document.createTextNode(`${s.writing ? "SCHREIBT" : count ? "ENTWURF" : "BEREIT"} · ${count} WÖRTER`));
    const control = frame.querySelector("#draft-control");
    control.textContent = s.writing ? "■ Stopp" : count ? "Neu formulieren" : "Antwort schreiben";
    const article = frame.querySelector("article");
    const wasAtEnd = window.scrollY + window.innerHeight >= document.body.scrollHeight - 80;
    if (article.dataset.text !== (s.reply || "")) {
      article.replaceChildren();
      for (const block of (s.reply || "").split(/\n\s*\n/)) {
        const paragraph = document.createElement("div");
        paragraph.style.whiteSpace = "pre-wrap";
        paragraph.textContent = block;
        article.append(paragraph);
      }
      article.dataset.text = s.reply || "";
    }
    article.hidden = !s.reply;
    const actions = frame.querySelector("#draft-actions");
    actions.hidden = !s.reply;
    actions.querySelectorAll("button").forEach(el => el.disabled = !!s.writing);
    const content = frame.firstElementChild;
    const needed = content.scrollHeight + 60;
    frame.dataset.originalHeight = Math.max(844, needed);
    resize();
    if (s.writing && wasAtEnd) window.scrollTo(0, document.body.scrollHeight);
  }
  function patchConsent(s) {
    const rows = frame.querySelectorAll("dl dd");
    // Preserve the original layout, but never turn its historical mock copy into a false
    // promise about today's direct API, retention, location, or billing.
    set(rows[0], s.personalApi ? "Google Gemini Developer API. Keine festgelegte EU-Region." : `Google Gemini, konfigurierte Region ${s.region || "EU"}.`);
    set(rows[1], "Verarbeitung nach den Bedingungen deines Google-API-Projekts.");
    set(rows[2], s.personalApi ? "API-Nutzung über dein Google-Projekt. Kosten sind möglich." : "Drei Prüfungen pro Tag sind in dieser Version frei.");
  }
  async function update(next) {
    state = next;
    if (!templates) return;
    const name = templateName(next);
    if (name !== current) install(name);
    applying = true;
    try {
      if (!capture && !next.example) {
        if (name.startsWith("Main-")) patchInput(next);
        else if (name.startsWith("Ergebnis-")) patchResult(next);
        else if (name === "Antwort") patchReply(next);
      }
      if (!capture && name === "Einwilligung") patchConsent(next);
      notice.hidden = !next.error;
      set(notice, next.error);
      resize();
    } finally { applying = false; }
    document.documentElement.dataset.ready = "true";
    document.documentElement.dataset.view = name;
  }
  notice.addEventListener("click", () => command("dismissError"));
  window.addEventListener("resize", resize);
  window.Briefampel = { update };
  fetch("templates.json").then(response => response.json()).then(data => {
    templates = data;
    if (window.BriefampelNative) command("ready");
    else update({ screen: "INPUT", example: true, hasInput: false });
  }).catch(() => { notice.hidden = false; set(notice, "Die lokale Darstellung konnte nicht geladen werden."); });
})();

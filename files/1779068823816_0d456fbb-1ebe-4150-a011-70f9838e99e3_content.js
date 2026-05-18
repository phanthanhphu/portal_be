
(function () {
  const FLAG = "YO_ITSM_AUTO_FLAG_V3";
  const DEBUG = "YO_ITSM_AUTO_DEBUG_V3";
  const OLD_FLAGS = [
    "YO_CLIENT_ONLY_OPEN_ITSM_AFTER_LOGIN_V2",
    "YO_CLIENT_ONLY_OPEN_ITSM_AFTER_LOGIN",
    "YO_AUTO_OPEN_ITSM_AFTER_LOGIN"
  ];
  const SSO_API = "https://gw2.youngone.com/groupware/pnPortal/getItsmSloParam.do";
  const ITSM_URL = "https://itsm.youngone.com/custom/youngone/index.do";
  const RETRY_MS = 800;
  const MAX_WAIT_MS = 30000;

  const getParams = () => {
    try { return new URLSearchParams(location.search); } catch (e) { return new URLSearchParams(""); }
  };

  const upper = (v) => String(v || "").trim().toUpperCase();
  const isLoginPage = () => location.pathname.includes("/covicore/login.do");

  function storageGet(keys) {
    return new Promise(resolve => {
      try { chrome.storage.local.get(keys, r => resolve(r || {})); }
      catch (e) { resolve({}); }
    });
  }

  function storageSet(obj) {
    return new Promise(resolve => {
      try { chrome.storage.local.set(obj, () => resolve()); }
      catch (e) { resolve(); }
    });
  }

  function storageRemove(keys) {
    return new Promise(resolve => {
      try { chrome.storage.local.remove(keys, () => resolve()); }
      catch (e) { resolve(); }
    });
  }

  function hasParam() {
    const p = getParams();
    return ["redirectItsm", "itsm", "openItsm"].some(k => {
      return ["Y", "YES", "TRUE", "1", "ITSM"].includes(upper(p.get(k)));
    });
  }

  function setCookie(v, age) {
    try { document.cookie = `${FLAG}=${encodeURIComponent(v)}; path=/; max-age=${age}; SameSite=Lax; Secure`; } catch (e) {}
    try { document.cookie = `${FLAG}=${encodeURIComponent(v)}; path=/; domain=.youngone.com; max-age=${age}; SameSite=Lax; Secure`; } catch (e) {}
  }

  function getCookieFlag() {
    const prefix = FLAG + "=";
    return (document.cookie || "").split(";").map(x => x.trim()).find(x => x.startsWith(prefix))?.slice(prefix.length) || "";
  }

  async function isDebug() {
    if (getParams().get("itsmDebug") === "Y") return true;
    try { if (localStorage.getItem("itsmDebug") === "Y") return true; } catch (e) {}
    const s = await storageGet([DEBUG]);
    return s[DEBUG] === "Y";
  }

  async function log(msg, data) {
    if (!(await isDebug())) return;
    try { console.log("[GW2-ITSM-EXT-V3]", msg, data || ""); } catch (e) {}
  }

  async function setFlag() {
    try { localStorage.setItem(FLAG, "Y"); } catch (e) {}
    try { sessionStorage.setItem(FLAG, "Y"); } catch (e) {}
    setCookie("Y", 600);
    await storageSet({ [FLAG]: "Y", [`${FLAG}_TIME`]: Date.now() });
  }

  async function hasFlag() {
    try { if (localStorage.getItem(FLAG) === "Y") return true; } catch (e) {}
    try { if (sessionStorage.getItem(FLAG) === "Y") return true; } catch (e) {}
    if (getCookieFlag() === "Y") return true;

    for (const k of OLD_FLAGS) {
      try { if (localStorage.getItem(k) === "Y") return true; } catch (e) {}
      try { if (sessionStorage.getItem(k) === "Y") return true; } catch (e) {}
    }

    const s = await storageGet([FLAG, ...OLD_FLAGS]);
    if (s[FLAG] === "Y") return true;
    return OLD_FLAGS.some(k => s[k] === "Y");
  }

  async function clearFlag() {
    try { localStorage.removeItem(FLAG); } catch (e) {}
    try { sessionStorage.removeItem(FLAG); } catch (e) {}
    setCookie("", 0);

    for (const k of OLD_FLAGS) {
      try { localStorage.removeItem(k); } catch (e) {}
      try { sessionStorage.removeItem(k); } catch (e) {}
    }

    await storageRemove([FLAG, `${FLAG}_TIME`, ...OLD_FLAGS]);
  }

  function cleanupUrl() {
    try {
      const u = new URL(location.href);
      ["redirectItsm", "itsm", "openItsm"].forEach(k => u.searchParams.delete(k));
      history.replaceState({}, document.title, u.toString());
    } catch (e) {}
  }

  function looksLogin(text) {
    const t = String(text || "").toLowerCase();
    return t.includes("<html") || t.includes("login.do") || t.includes("loginchk.do");
  }

  function lang() {
    try {
      const m = document.cookie.match(/(?:^|;\s*)langCode=([^;]+)/);
      if (m) return decodeURIComponent(m[1]).toUpperCase();
    } catch (e) {}
    return "EN";
  }

  async function getSso() {
    await log("calling SSO API", { url: SSO_API });
    const res = await fetch(SSO_API, {
      method: "POST",
      credentials: "include",
      headers: {
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest"
      },
      body: ""
    });

    const text = await res.text();
    await log("SSO response", { status: res.status, preview: text.slice(0, 250) });

    if (!res.ok) throw new Error("SSO HTTP " + res.status);
    if (looksLogin(text)) throw new Error("GW2 session not ready");

    const data = JSON.parse(text);
    if (!data || data.status !== "SUCCESS" || !data.jsonData) throw new Error("SSO not SUCCESS");

    const p = {
      olmI: data.jsonData.olml || data.jsonData.olmI || "",
      companyCode: data.jsonData.companyCode || "",
      teamCode: data.jsonData.teamCode || "",
      langCode: lang()
    };

    if (!p.olmI || !p.companyCode || !p.teamCode) throw new Error("missing ITSM params");
    return p;
  }

  async function postItsm(p) {
    await log("posting ITSM", p);
    await clearFlag();

    const f = document.createElement("form");
    f.method = "POST";
    f.action = ITSM_URL;
    f.target = "_blank";
    f.style.display = "none";

    Object.entries(p).forEach(([k, v]) => {
      const i = document.createElement("input");
      i.type = "hidden";
      i.name = k;
      i.value = v || "";
      f.appendChild(i);
    });

    document.documentElement.appendChild(f);
    f.submit();
  }

  async function runLoop() {
    if (!(await hasFlag())) return;

    const start = Date.now();

    async function attempt() {
      if (!(await hasFlag())) return;

      try {
        const p = await getSso();
        await postItsm(p);
      } catch (e) {
        await log("retry", { error: e.message || String(e) });
        if (Date.now() - start < MAX_WAIT_MS) setTimeout(attempt, RETRY_MS);
      }
    }

    attempt();
  }

  async function main() {
    if (getParams().get("itsmDebug") === "Y") {
      try { localStorage.setItem("itsmDebug", "Y"); } catch (e) {}
      await storageSet({ [DEBUG]: "Y" });
    }

    const redirect = hasParam();
    if (redirect) {
      await setFlag();
      cleanupUrl();
    }

    const flag = await hasFlag();

    await log("Extension content loaded", {
      href: location.href,
      hasRedirectItsmParam: redirect,
      hasFlag: flag,
      isLoginPage: isLoginPage()
    });

    if (!flag) return;
    if (isLoginPage()) {
      await log("login page has flag, waiting for Home");
      return;
    }

    await runLoop();
  }

  main();
})();

/**
 * 智慧食堂 - 共享 JS 库
 * 提供：API 客户端（含 Token 自动刷新）、Toast 通知、登录/登出、工具函数
 */
"use strict";

// ===== 配置 =====
var API_BASE = "http://localhost:8080";
var ACCESS_TOKEN = localStorage.getItem("_tk") || "";
var REFRESH_TOKEN = localStorage.getItem("_rtk") || "";
var CURRENT_USER = null;

// ===== 商户/状态映射 =====
var MERCHANTS = {
  1: { name: "川味窗口", counter: "C01" },
  2: { name: "粤式窗口", counter: "C02" },
  3: { name: "面食窗口", counter: "C03" }
};

var STATUS_LABELS = {
  PLACED: "已下单", ACCEPTED: "已接单", PREPARING: "制作中",
  WAITING_PICKUP: "待取餐", PICKED_UP: "已取餐", CANCELED: "已取消"
};

var ROLE_LABELS = {
  user: "普通用户", merchant: "商家", admin: "系统管理员"
};

function statusLabel(s) {
  return STATUS_LABELS[s] || s;
}

function roleLabel(r) {
  return ROLE_LABELS[r] || r || "普通用户";
}

// ===== Toast 通知 =====
var tc = document.getElementById("toast-container");
if (!tc) {
  // 自动注入 toast 容器
  tc = document.createElement("div");
  tc.id = "toast-container";
  document.body.appendChild(tc);
}

function toast(msg, type) {
  var d = document.createElement("div");
  d.className = "toast " + (type || "ok");
  d.textContent = msg;
  tc.appendChild(d);
  setTimeout(function () {
    d.style.opacity = "0";
    d.style.transition = "opacity .3s";
    setTimeout(function () { d.remove(); }, 300);
  }, 2500);
}

// ===== Token 刷新（防并发） =====
var _isRefreshing = false;
var _refreshQueue = [];

async function refreshToken() {
  if (!REFRESH_TOKEN) return false;
  try {
    var r = await fetch(API_BASE + "/api/user/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: REFRESH_TOKEN })
    });
    var j = await r.json();
    if (j.code === 0 && j.data) {
      ACCESS_TOKEN = j.data.accessToken;
      REFRESH_TOKEN = j.data.refreshToken;
      localStorage.setItem("_tk", ACCESS_TOKEN);
      localStorage.setItem("_rtk", REFRESH_TOKEN);
      return true;
    }
  } catch (e) {
    console.error("Token refresh failed:", e);
  }
  return false;
}

// ===== API 客户端（含自动刷新） =====
async function api(url, opts) {
  opts = opts || {};
  var headers = opts.headers || { "Content-Type": "application/json" };
  if (ACCESS_TOKEN) {
    headers["Authorization"] = "Bearer " + ACCESS_TOKEN;
  }
  opts.headers = headers;

  try {
    var r = await fetch(API_BASE + url, opts);

    // 401: 尝试刷新 token
    if (r.status === 401 && REFRESH_TOKEN) {
      // 防止并发刷新
      if (!_isRefreshing) {
        _isRefreshing = true;
        var ok = await refreshToken();
        _isRefreshing = false;
        if (ok) {
          // 重试原请求
          opts.headers["Authorization"] = "Bearer " + ACCESS_TOKEN;
          r = await fetch(API_BASE + url, opts);
        } else {
          // 刷新失败，清除状态
          forceLogout();
          return { code: 401, message: "登录已过期，请重新登录" };
        }
      } else {
        // 等待他人刷新完成
        await new Promise(function (res) { return setTimeout(res, 300); });
        if (ACCESS_TOKEN) {
          opts.headers["Authorization"] = "Bearer " + ACCESS_TOKEN;
          r = await fetch(API_BASE + url, opts);
        } else {
          forceLogout();
          return { code: 401, message: "登录已过期，请重新登录" };
        }
      }
    }

    // 再次检查 401（刷新后仍失败）
    if (r.status === 401) {
      forceLogout();
      return { code: 401, message: "登录已过期，请重新登录" };
    }

    var j = await r.json();
    return j;
  } catch (e) {
    toast("网络错误：" + e.message, "er");
    return { code: -1, message: e.message };
  }
}

// ===== 用户状态管理 =====
function updateUserBar() {
  var ub = document.getElementById("user-bar");
  if (!ub) return;
  var unameEl = document.getElementById("uname");
  var btnEl = document.getElementById("btn-login-out");
  if (!unameEl || !btnEl) return;

  if (CURRENT_USER) {
    unameEl.textContent = CURRENT_USER.nickname || CURRENT_USER.phone || "用户";
    btnEl.textContent = "退出";
    btnEl.onclick = logout;
  } else {
    unameEl.textContent = "未登录";
    btnEl.textContent = "登录";
    btnEl.onclick = showLogin;
  }
}

function forceLogout() {
  ACCESS_TOKEN = "";
  REFRESH_TOKEN = "";
  localStorage.removeItem("_tk");
  localStorage.removeItem("_rtk");
  CURRENT_USER = null;
  updateUserBar();
  toast("登录已过期，请重新登录", "er");
}

async function logout() {
  // 调用后端登出（fire-and-forget）
  if (ACCESS_TOKEN) {
    try {
      await api("/api/user/auth/logout", { method: "POST" });
    } catch (e) { /* ignore */ }
  }
  ACCESS_TOKEN = "";
  REFRESH_TOKEN = "";
  localStorage.removeItem("_tk");
  localStorage.removeItem("_rtk");
  CURRENT_USER = null;
  updateUserBar();
  toast("已退出登录");
}

// ===== 登录/注册弹窗 =====
var LOGIN_TAB = "login";

function showLogin() {
  var modal = document.getElementById("login-modal");
  if (modal) modal.classList.remove("hidden");
  switchLoginTab("login");
}

function hideLogin() {
  var modal = document.getElementById("login-modal");
  if (modal) modal.classList.add("hidden");
}

function switchLoginTab(t) {
  LOGIN_TAB = t;
  var lb = document.getElementById("ltab-login");
  var rb = document.getElementById("ltab-register");
  if (!lb || !rb) return;
  if (t === "login") {
    lb.style.cssText = "border-radius:20px 0 0 20px;background:var(--p);color:#fff";
    rb.style.cssText = "border-radius:0 20px 20px 0;background:#fff;color:var(--t)";
  } else {
    lb.style.cssText = "border-radius:20px 0 0 20px;background:#fff;color:var(--t)";
    rb.style.cssText = "border-radius:0 20px 20px 0;background:var(--p);color:#fff";
  }
  var loginForm = document.getElementById("login-form");
  var registerForm = document.getElementById("register-form");
  if (loginForm) loginForm.classList.toggle("hidden", t !== "login");
  if (registerForm) registerForm.classList.toggle("hidden", t !== "register");
}

async function doLogin() {
  var phone = document.getElementById("l-phone").value.trim();
  var pass = document.getElementById("l-pass").value.trim();
  if (!phone || !pass) { toast("请填写手机号和密码", "er"); return; }

  var d = await api("/api/user/auth/login", {
    method: "POST",
    body: JSON.stringify({ phone: phone, password: pass, loginType: "password" })
  });
  if (d.code !== 0) { toast(d.message, "er"); return; }

  ACCESS_TOKEN = d.data.accessToken;
  REFRESH_TOKEN = d.data.refreshToken;
  localStorage.setItem("_tk", ACCESS_TOKEN);
  localStorage.setItem("_rtk", REFRESH_TOKEN);

  CURRENT_USER = { phone: phone };
  updateUserBar();
  hideLogin();
  toast("登录成功！欢迎回来", "ok");

  // 获取用户完整信息
  try {
    var ud = await api("/api/user/users/me");
    if (ud.code === 0 && ud.data) { CURRENT_USER = ud.data; updateUserBar(); }
  } catch (e) { /* ignore */ }

  // 通知页面登录成功（页面可自定义此函数）
  if (typeof onLoginSuccess === "function") onLoginSuccess();
}

async function doRegister() {
  var phone = document.getElementById("r-phone").value.trim();
  var nick = document.getElementById("r-nick").value.trim();
  var pass = document.getElementById("r-pass").value.trim();
  if (!phone || !nick || !pass) { toast("请填写所有字段", "er"); return; }
  if (pass.length < 6) { toast("密码至少6位", "er"); return; }

  var d = await api("/api/user/auth/register", {
    method: "POST",
    body: JSON.stringify({ phone: phone, nickname: nick, password: pass })
  });
  if (d.code !== 0) { toast(d.message, "er"); return; }

  toast("注册成功！请登录", "ok");
  switchLoginTab("login");
  document.getElementById("l-phone").value = phone;
  document.getElementById("l-pass").value = "";
}

// ===== 初始化 =====
async function initAuth() {
  if (ACCESS_TOKEN) {
    try {
      var ud = await api("/api/user/users/me");
      if (ud.code === 0 && ud.data) {
        CURRENT_USER = ud.data;
        updateUserBar();
        if (typeof onLoginSuccess === "function") onLoginSuccess();
        return;
      }
    } catch (e) { /* ignore */ }
    // Token 无效，清除
    forceLogout();
  }
  updateUserBar();
}

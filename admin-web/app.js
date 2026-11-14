import { initializeApp } from "https://www.gstatic.com/firebasejs/11.4.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-auth.js";
import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-firestore.js";
import { FIREBASE_WEB_CONFIG, FIRESTORE_DATABASE_ID, OWNER_EMAIL } from "./firebase-config.js";

const el = (id) => document.getElementById(id);

function showToast(msg) {
  const t = el("toast");
  t.textContent = msg;
  t.classList.remove("hidden");
  setTimeout(() => t.classList.add("hidden"), 3500);
}

// Theme toggle
el("themeToggleBtn").addEventListener("click", () => {
  const dark = document.body.classList.toggle("dark-space");
  el("themeToggleBtn").textContent = dark ? "White Crystal" : "Dark Space";
});

// Tab switching
document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tab-btn").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach((p) => p.classList.add("hidden"));
    btn.classList.add("active");
    el(`tab-${btn.dataset.tab}`).classList.remove("hidden");
  });
});

const hasPlaceholderConfig =
  FIREBASE_WEB_CONFIG.apiKey.startsWith("REPLACE_") ||
  FIREBASE_WEB_CONFIG.appId.startsWith("REPLACE_");

if (hasPlaceholderConfig) {
  el("configWarning").classList.remove("hidden");
}

const app = initializeApp(FIREBASE_WEB_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DATABASE_ID);
const googleProvider = new GoogleAuthProvider();

let cachedUsers = [];

async function verifyAdminClaims(user, forceRefresh = false) {
  const email = (user.email || "").trim().toLowerCase();
  if (email === OWNER_EMAIL.toLowerCase()) return true;
  const tokenResult = await user.getIdTokenResult(forceRefresh);
  const claims = tokenResult.claims || {};
  return claims.admin === true || claims.role === "admin";
}

async function writeAuditLog(action, targetId, details) {
  const actor = auth.currentUser;
  if (!actor) return;
  await addDoc(collection(db, "audit_logs"), {
    actorUid: actor.uid,
    actorEmail: actor.email || "",
    action,
    targetId,
    details,
    createdAt: serverTimestamp()
  });
}

async function loadMetrics() {
  const snap = await getDocs(query(collection(db, "usage_metrics"), orderBy("createdAt", "desc"), limit(200)));
  let totalTokens = 0;
  const byProvider = {};
  snap.forEach((d) => {
    const data = d.data();
    totalTokens += Number(data.tokensUsed || 0);
    const pid = data.providerId || "unknown";
    byProvider[pid] = (byProvider[pid] || 0) + 1;
  });

  el("statTotalRuns").textContent = String(snap.size);
  el("statTotalTokens").textContent = totalTokens.toLocaleString();

  const container = el("providerBreakdown");
  container.innerHTML = "";
  const maxVal = Math.max(1, ...Object.values(byProvider));
  Object.entries(byProvider).forEach(([provider, count]) => {
    const pct = Math.round((count / maxVal) * 100);
    const row = document.createElement("div");
    row.className = "bar-row";
    row.innerHTML = `
      <div style="display:flex;justify-content:space-between">
        <strong>${provider.toUpperCase()}</strong>
        <span>${count} runs</span>
      </div>
      <div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div>
    `;
    container.appendChild(row);
  });
  if (snap.empty) {
    container.innerHTML = `<p class="muted">No usage telemetry recorded yet.</p>`;
  }
}

function renderUsersTable(filterText = "") {
  const body = el("usersTableBody");
  body.innerHTML = "";
  const q = filterText.trim().toLowerCase();
  const filtered = cachedUsers.filter(
    (u) =>
      !q ||
      (u.email || "").toLowerCase().includes(q) ||
      (u.displayName || "").toLowerCase().includes(q) ||
      u.id.toLowerCase().includes(q)
  );

  filtered.forEach((u) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><strong>${escapeHtml(u.displayName || "User")}</strong><br><span class="muted">${escapeHtml(u.email || u.id)}</span></td>
      <td>
        <select class="role-sel">
          <option value="user" ${u.role === "user" ? "selected" : ""}>user</option>
          <option value="moderator" ${u.role === "moderator" ? "selected" : ""}>moderator</option>
          <option value="admin" ${u.role === "admin" ? "selected" : ""}>admin</option>
        </select>
      </td>
      <td>
        <select class="status-sel">
          <option value="active" ${u.status === "active" ? "selected" : ""}>active</option>
          <option value="suspended" ${u.status === "suspended" ? "selected" : ""}>suspended</option>
        </select>
      </td>
      <td><input class="input limit-inp" type="number" min="1" max="100000" value="${Number(u.dailyLimit || 200)}" style="width:90px" /></td>
      <td><input class="input budget-inp" type="number" step="0.5" min="0" max="10000" value="${Number(u.monthlyBudgetUsd || 25)}" style="width:90px" /></td>
      <td><button class="btn btn-primary save-user-btn" type="button">Save</button></td>
    `;
    tr.querySelector(".save-user-btn").addEventListener("click", async () => {
      const role = tr.querySelector(".role-sel").value;
      const status = tr.querySelector(".status-sel").value;
      const dailyLimit = Number(tr.querySelector(".limit-inp").value);
      const monthlyBudgetUsd = Number(tr.querySelector(".budget-inp").value);
      try {
        await updateDoc(doc(db, "users", u.id), {
          role,
          status,
          dailyLimit,
          monthlyBudgetUsd,
          updatedAt: serverTimestamp()
        });
        await writeAuditLog("user_governance_update", u.id, `role=${role}, status=${status}, dailyLimit=${dailyLimit}`);
        showToast(`Updated ${u.email || u.id}`);
        await loadAuditLogs();
      } catch (err) {
        showToast(`Update failed: ${err.message}`);
      }
    });
    body.appendChild(tr);
  });
}

async function loadUsers() {
  const snap = await getDocs(query(collection(db, "users"), limit(200)));
  cachedUsers = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  el("statTotalUsers").textContent = String(cachedUsers.length);
  renderUsersTable(el("userSearchInput").value);
}

async function loadGlobalConfig() {
  const snap = await getDoc(doc(db, "system_config", "global"));
  if (snap.exists()) {
    const data = snap.data();
    el("cfgDefaultModel").value = data.defaultModel || "gemini-3.8-flash";
    el("cfgAnnouncement").value = data.announcement || "";
    el("cfgMaintenanceMode").checked = Boolean(data.maintenanceMode);
    if (Array.isArray(data.allowedProviders)) {
      el("cfgAllowedProviders").value = data.allowedProviders.join(",");
    }
  }
}

async function loadAuditLogs() {
  const snap = await getDocs(query(collection(db, "audit_logs"), orderBy("createdAt", "desc"), limit(50)));
  const list = el("auditLogsList");
  list.innerHTML = "";
  snap.forEach((d) => {
    const item = d.data();
    const div = document.createElement("div");
    div.className = "audit-item";
    div.innerHTML = `
      <div><strong>${escapeHtml(item.action)}</strong> → <code>${escapeHtml(item.targetId)}</code></div>
      <div class="muted">By ${escapeHtml(item.actorEmail || item.actorUid)} • ${escapeHtml(item.details || "")}</div>
    `;
    list.appendChild(div);
  });
  if (snap.empty) {
    list.innerHTML = `<p class="muted">No audit events recorded yet.</p>`;
  }
}

function escapeHtml(str) {
  return String(str || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function refreshAllAdminData() {
  await Promise.all([loadUsers(), loadMetrics(), loadGlobalConfig(), loadAuditLogs()]);
}

getRedirectResult(auth).catch((err) => {
  if (err && err.message) el("authStatusMsg").textContent = err.message;
});

el("googleSignInBtn").addEventListener("click", async () => {
  el("authStatusMsg").textContent = "Opening Google Sign-In...";
  try {
    await signInWithPopup(auth, googleProvider);
  } catch (err) {
    if (err.code === "auth/popup-blocked" || err.code === "auth/cancelled-popup-request" || /popup/i.test(err.message)) {
      await signInWithRedirect(auth, googleProvider);
    } else {
      el("authStatusMsg").textContent = "Login Error: " + err.message;
    }
  }
});

const emailForm = el("emailLoginForm");
if (emailForm) {
  emailForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = el("adminEmailInput").value.trim();
    const pass = el("adminPasswordInput").value;
    if (email.toLowerCase() !== OWNER_EMAIL.toLowerCase()) {
      el("authStatusMsg").textContent = "Access Denied: Only owner email is permitted.";
      return;
    }
    el("authStatusMsg").textContent = "Signing in...";
    try {
      await signInWithEmailAndPassword(auth, email, pass);
    } catch (err) {
      el("authStatusMsg").textContent = "Login Error: " + err.message;
    }
  });
}

el("signOutBtn").addEventListener("click", () => signOut(auth));
el("unauthSignOutBtn").addEventListener("click", () => signOut(auth));
el("recheckClaimsBtn").addEventListener("click", async () => {
  const user = auth.currentUser;
  if (!user) return;
  const ok = await verifyAdminClaims(user, true);
  if (ok) {
    el("unauthorizedGate").classList.add("hidden");
    el("adminDashboard").classList.remove("hidden");
    await refreshAllAdminData();
  } else {
    showToast("Admin custom claim still not present on token.");
  }
});

el("userSearchInput").addEventListener("input", (e) => renderUsersTable(e.target.value));
el("refreshMetricsBtn").addEventListener("click", loadMetrics);
el("refreshAuditBtn").addEventListener("click", loadAuditLogs);

el("globalConfigForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    defaultModel: el("cfgDefaultModel").value.trim() || "gemini-3.8-flash",
    announcement: el("cfgAnnouncement").value.trim(),
    maintenanceMode: el("cfgMaintenanceMode").checked,
    allowedProviders: el("cfgAllowedProviders").value
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
    updatedAt: serverTimestamp()
  };
  try {
    await setDoc(doc(db, "system_config", "global"), payload, { merge: true });
    await writeAuditLog("system_config_update", "global", `defaultModel=${payload.defaultModel}, maintenance=${payload.maintenanceMode}`);
    showToast("Global system config saved.");
    await loadAuditLogs();
  } catch (err) {
    showToast(`Failed to save config: ${err.message}`);
  }
});

onAuthStateChanged(auth, async (user) => {
  if (!user) {
    el("userChip").classList.add("hidden");
    el("authGate").classList.remove("hidden");
    el("unauthorizedGate").classList.add("hidden");
    el("adminDashboard").classList.add("hidden");
    return;
  }

  el("userEmailLabel").textContent = user.email || user.uid;
  el("userChip").classList.remove("hidden");
  el("authGate").classList.add("hidden");

  const isAdmin = await verifyAdminClaims(user, false);
  if (!isAdmin) {
    el("unauthEmail").textContent = user.email || user.uid;
    el("unauthorizedGate").classList.remove("hidden");
    el("adminDashboard").classList.add("hidden");
    await signOut(auth);
    return;
  }

  el("unauthorizedGate").classList.add("hidden");
  el("adminDashboard").classList.remove("hidden");
  try {
    await refreshAllAdminData();
  } catch (err) {
    showToast(`Error loading admin console: ${err.message}`);
  }
});

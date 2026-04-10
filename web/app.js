
// -----------------------------------------------------------------------------
// Constants
// -----------------------------------------------------------------------------

const API = {
  stats:    '/api/stats',
  reset:    '/api/reset',
  rooms:    '/api/rooms/batch',
  students: '/api/students/batch',
  allocate: '/api/allocate',
  view:     '/api/view',
  analyze:  '/api/analyze',
};

// Branch → colour mapping.
// Extend this object if you add new branches.
const BRANCH_COLORS = {
  CSE:  { bg: '#dbeafe', border: '#3b82f6', text: '#1d4ed8' }, // Blue
  ECE:  { bg: '#dcfce7', border: '#22c55e', text: '#15803d' }, // Green
  ME:   { bg: '#fef9c3', border: '#eab308', text: '#854d0e' }, // Yellow
  CE:   { bg: '#fce7f3', border: '#ec4899', text: '#9d174d' }, // Pink
  IT:   { bg: '#ede9fe', border: '#8b5cf6', text: '#5b21b6' }, // Purple
  EEE:  { bg: '#ffedd5', border: '#f97316', text: '#9a3412' }, // Orange
  DEFAULT: { bg: '#f3f4f6', border: '#9ca3af', text: '#374151' },
};

// -----------------------------------------------------------------------------
// State
// -----------------------------------------------------------------------------

let statsInterval = null; // Auto-refresh handle for dashboard

// -----------------------------------------------------------------------------
// Initialisation
// -----------------------------------------------------------------------------

document.addEventListener('DOMContentLoaded', () => {
  initRoomInputs();
  initStudentInputs();
  loadStats();

  // Refresh stats every 10 seconds while on dashboard
  statsInterval = setInterval(loadStats, 10_000);
});

// -----------------------------------------------------------------------------
// Tab Navigation
// -----------------------------------------------------------------------------

function showTab(name) {
  // Hide all tabs and deactivate all nav links
  document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

  // Show requested tab
  const tab  = document.getElementById('tab-' + name);
  const link = document.getElementById('link-' + name);
  if (tab)  tab.classList.remove('hidden');
  if (link) link.classList.add('active');

  // Update header title
  const titles = { dashboard: 'Dashboard', config: 'Configuration', map: 'Seating Map' };
  const el = document.getElementById('page-title');
  if (el) el.textContent = titles[name] || name;

  // Auto-load map when switching to it
  if (name === 'map') loadMap();
}

// -----------------------------------------------------------------------------
// Dashboard — Stats Cards
// -----------------------------------------------------------------------------

async function loadStats() {
  try {
    const data = await get(API.stats);
    setText('stat-rooms',     data.rooms     ?? 0);
    setText('stat-capacity',  data.capacity  ?? 0);
    setText('stat-students',  data.students  ?? 0);
    setText('stat-allocated', data.allocated ?? 0);
  } catch (e) {
    // Silent fail — stats are non-critical
    console.warn('[Stats] Could not load:', e.message);
  }
}

// -----------------------------------------------------------------------------
// Configuration — Room Input Rows
// -----------------------------------------------------------------------------

function initRoomInputs() {
  const container = document.getElementById('room-inputs');
  if (container && container.children.length === 0) addRoomRow();
}

function addRoomRow() {
  const container = document.getElementById('room-inputs');
  const row = document.createElement('div');
  row.className = 'input-row';
  row.innerHTML = `
    <input type="text"   placeholder="Room name (e.g. Hall-A)"  class="room-name" />
    <input type="number" placeholder="Rows (e.g. 5)"  min="1" max="20" class="room-rows" style="width:100px"/>
    <input type="number" placeholder="Cols (e.g. 6)"  min="1" max="20" class="room-cols" style="width:100px"/>
    <button onclick="this.parentElement.remove()" class="btn-remove" title="Remove">✕</button>
  `;
  container.appendChild(row);
}

async function saveRooms() {
  const rows = document.querySelectorAll('#room-inputs .input-row');
  const parts = [];

  for (const row of rows) {
    const name = row.querySelector('.room-name')?.value?.trim();
    const r    = row.querySelector('.room-rows')?.value?.trim();
    const c    = row.querySelector('.room-cols')?.value?.trim();

    if (!name || !r || !c) { showToast('Fill all room fields before saving.', 'error'); return; }
    if (parseInt(r) < 1 || parseInt(c) < 1) { showToast('Rows and columns must be ≥ 1.', 'error'); return; }
    parts.push(`${name},${r},${c}`);
  }

  if (parts.length === 0) { showToast('Add at least one room.', 'error'); return; }

  const btn = document.querySelector('[onclick="saveRooms()"]');
  setLoading(btn, true, 'Saving...');

  try {
    await post(API.rooms, parts.join(';'));
    showToast(`${parts.length} room(s) saved successfully.`, 'success');
    loadStats();
  } catch (e) {
    showToast('Failed to save rooms: ' + e.message, 'error');
  } finally {
    setLoading(btn, false, 'Save Rooms');
  }
}

// -----------------------------------------------------------------------------
// Configuration — Student Input Rows
// -----------------------------------------------------------------------------

function initStudentInputs() {
  const container = document.getElementById('student-inputs');
  if (container && container.children.length === 0) addStudentRow();
}

function addStudentRow() {
  const container = document.getElementById('student-inputs');
  const row = document.createElement('div');
  row.className = 'input-row';
  row.innerHTML = `
    <input type="text"   placeholder="Branch (e.g. CSE)"    class="st-branch" style="width:110px"/>
    <input type="number" placeholder="From (e.g. 1)"  min="1" class="st-from" style="width:90px"/>
    <input type="number" placeholder="To   (e.g. 30)" min="1" class="st-to"   style="width:90px"/>
    <button onclick="this.parentElement.remove()" class="btn-remove" title="Remove">✕</button>
  `;
  container.appendChild(row);
}

async function saveStudents() {
  const rows = document.querySelectorAll('#student-inputs .input-row');
  const parts = [];

  for (const row of rows) {
    const branch = row.querySelector('.st-branch')?.value?.trim().toUpperCase();
    const from   = row.querySelector('.st-from')?.value?.trim();
    const to     = row.querySelector('.st-to')?.value?.trim();

    if (!branch || !from || !to) { showToast('Fill all student batch fields.', 'error'); return; }
    if (parseInt(from) > parseInt(to)) { showToast(`"From" must be ≤ "To" for branch ${branch}.`, 'error'); return; }
    parts.push(`${branch},${from},${to}`);
  }

  if (parts.length === 0) { showToast('Add at least one student batch.', 'error'); return; }

  const total = parts.reduce((sum, p) => {
    const [, f, t] = p.split(',');
    return sum + (parseInt(t) - parseInt(f) + 1);
  }, 0);

  const btn = document.querySelector('[onclick="saveStudents()"]');
  setLoading(btn, true, 'Saving...');

  try {
    await post(API.students, parts.join(';'));
    showToast(`${total} student(s) across ${parts.length} batch(es) registered.`, 'success');
    loadStats();
  } catch (e) {
    showToast('Failed to save students: ' + e.message, 'error');
  } finally {
    setLoading(btn, false, 'Save Students');
  }
}

// -----------------------------------------------------------------------------
// Allocation — Run AI
// -----------------------------------------------------------------------------

async function runAI() {
  const btn = document.querySelector('[onclick="runAI()"]');
  setLoading(btn, true, '⏳ Running Algorithm...');

  try {
    await post(API.allocate, '');
    showToast('Allocation complete! Go to Seating Map to view results.', 'success');
    loadStats();
  } catch (e) {
    showToast('Allocation failed: ' + e.message, 'error');
  } finally {
    setLoading(btn, false, '✨ Run AI Allocation');
  }
}

// -----------------------------------------------------------------------------
// Reset
// -----------------------------------------------------------------------------

async function resetSystem() {
  if (!confirm('This will delete ALL rooms, students, and allocations. Are you sure?')) return;

  try {
    await post(API.reset, '');
    showToast('System reset. All data cleared.', 'success');
    loadStats();

    // Clear the seating map
    const grid = document.getElementById('map-grid');
    if (grid) grid.innerHTML = emptyState('System reset. Add rooms and students to begin.');
  } catch (e) {
    showToast('Reset failed: ' + e.message, 'error');
  }
}

// -----------------------------------------------------------------------------
// Seating Map
// -----------------------------------------------------------------------------

async function loadMap() {
  const grid = document.getElementById('map-grid');
  if (!grid) return;

  grid.innerHTML = `<div class="loading-state"><div class="spinner"></div><p>Loading seating map...</p></div>`;

  try {
    const seats = await get(API.view);

    if (!seats || seats.length === 0) {
      grid.innerHTML = emptyState('No allocation yet. Go to Configuration and run the AI.');
      return;
    }

    // Group seats by room name
    const roomMap = new Map(); // roomName → [seat, ...]
    for (const seat of seats) {
      if (!roomMap.has(seat.room)) roomMap.set(seat.room, []);
      roomMap.get(seat.room).push(seat);
    }

    // Render each room as a separate section
    grid.innerHTML = '';
    for (const [roomName, roomSeats] of roomMap) {
      grid.appendChild(buildRoomSection(roomName, roomSeats));
    }

  } catch (e) {
    grid.innerHTML = emptyState('Failed to load map: ' + e.message);
    showToast('Could not load seating map.', 'error');
  }
}

/**
 * Builds a room section: header + seat grid.
 *
 * Seats are laid out in a CSS grid matching the actual physical room layout
 * (rows × cols). Each seat card is coloured by branch.
 */
function buildRoomSection(roomName, seats) {
  // Determine grid dimensions from the data
  const maxCol = Math.max(...seats.map(s => s.col));
  const maxRow = Math.max(...seats.map(s => s.row));

  // Build a lookup: "row-col" → seat
  const lookup = {};
  for (const seat of seats) lookup[`${seat.row}-${seat.col}`] = seat;

  // Section wrapper
  const section = document.createElement('div');
  section.className = 'room-section';

  // Room header
  const header = document.createElement('div');
  header.className = 'room-header';
  header.innerHTML = `
    <span class="material-icons">meeting_room</span>
    <h3>${roomName}</h3>
    <span class="room-meta">${seats.length} students · ${maxRow} rows × ${maxCol} cols</span>
    ${buildBranchLegend(seats)}
  `;
  section.appendChild(header);

  // Seat grid — laid out as an actual rows×cols grid
  const seatGrid = document.createElement('div');
  seatGrid.className = 'seat-grid';
  seatGrid.style.gridTemplateColumns = `repeat(${maxCol}, 1fr)`;

  for (let r = 1; r <= maxRow; r++) {
    for (let c = 1; c <= maxCol; c++) {
      const seat = lookup[`${r}-${c}`];
      seatGrid.appendChild(seat ? buildSeatCard(seat) : buildEmptySeat(r, c));
    }
  }

  section.appendChild(seatGrid);
  return section;
}

/**
 * Builds a single seat card with branch colour coding.
 */
function buildSeatCard(seat) {
  const color = BRANCH_COLORS[seat.branch] || BRANCH_COLORS.DEFAULT;
  const card = document.createElement('div');
  card.className = 'seat-card';
  card.style.cssText = `
    background: ${color.bg};
    border-color: ${color.border};
  `;
  card.title = `${seat.name}\n${seat.roll}\nRow ${seat.row}, Col ${seat.col}`;
  card.innerHTML = `
    <span class="seat-number">R${seat.row}C${seat.col}</span>
    <span class="seat-roll">${seat.roll}</span>
    <span class="seat-branch" style="color:${color.text};background:${color.bg};border-color:${color.border}">
      ${seat.branch}
    </span>
  `;
  return card;
}

function buildEmptySeat(r, c) {
  const card = document.createElement('div');
  card.className = 'seat-card seat-empty';
  card.innerHTML = `<span class="seat-number">R${r}C${c}</span><span style="color:#ccc;font-size:0.75rem">Empty</span>`;
  return card;
}

/**
 * Builds a compact legend showing which branches are in this room.
 */
function buildBranchLegend(seats) {
  const branches = [...new Set(seats.map(s => s.branch))].sort();
  const chips = branches.map(b => {
    const c = BRANCH_COLORS[b] || BRANCH_COLORS.DEFAULT;
    return `<span class="legend-chip" style="background:${c.bg};color:${c.text};border-color:${c.border}">${b}</span>`;
  }).join('');
  return `<div class="branch-legend">${chips}</div>`;
}

// -----------------------------------------------------------------------------
// AI Analysis (Phase 5 — wired up now, full response in Phase 5)
// -----------------------------------------------------------------------------

async function runAnalysis() {
  const panel = document.getElementById('ai-insight');
  if (!panel) return;

  panel.innerHTML = `<div class="ai-loading"><div class="spinner"></div> Analysing seating arrangement...</div>`;

  try {
    const data = await post(API.analyze, '');
    panel.innerHTML = `
      <div class="ai-result">
        <span class="material-icons ai-icon">psychology</span>
        <p>${data.insight}</p>
      </div>`;
  } catch (e) {
    panel.innerHTML = `<p class="ai-error">Analysis failed: ${e.message}</p>`;
  }
}

// -----------------------------------------------------------------------------
// Utilities — HTTP
// -----------------------------------------------------------------------------

async function get(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function post(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

// -----------------------------------------------------------------------------
// Utilities — UI
// -----------------------------------------------------------------------------

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function setLoading(btn, loading, label) {
  if (!btn) return;
  btn.disabled = loading;
  btn.textContent = label;
}

function emptyState(msg) {
  return `<div class="empty-state">
    <span class="material-icons">info</span>
    <p>${msg}</p>
  </div>`;
}

// -----------------------------------------------------------------------------
// Toast Notifications
// replaces alert() — shows a dismissible message at bottom-right
// -----------------------------------------------------------------------------

function showToast(message, type = 'info') {
  // Create container if it doesn't exist
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  const icons = { success: 'check_circle', error: 'error', info: 'info' };
  toast.innerHTML = `
    <span class="material-icons">${icons[type] || 'info'}</span>
    <span>${message}</span>
  `;
  container.appendChild(toast);

  // Auto-dismiss after 4 seconds
  setTimeout(() => {
    toast.classList.add('toast-hide');
    setTimeout(() => toast.remove(), 400);
  }, 4000);
}
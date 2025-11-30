const API = "http://localhost:8080/api";

window.onload = () => {
    loadStats();
    addRoomRow();
    addStudentRow();
};

// --- NAVIGATION ---
function showTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    document.getElementById(`tab-${tabId}`).classList.remove('hidden');
    document.getElementById(`link-${tabId}`).classList.add('active');

    const titles = {'dashboard': 'Dashboard', 'config': 'System Configuration', 'map': 'Seating Map'};
    document.getElementById('page-title').innerText = titles[tabId];

    if(tabId === 'dashboard') loadStats();
    if(tabId === 'map') loadMap();
}

// --- API: DASHBOARD ---
async function loadStats() {
    const res = await fetch(`${API}/stats`);
    const data = await res.json();
    document.getElementById('stat-rooms').innerText = data.rooms;
    document.getElementById('stat-capacity').innerText = data.capacity;
    document.getElementById('stat-students').innerText = data.students;
    document.getElementById('stat-allocated').innerText = data.allocated;
}

// --- API: CONFIGURATION ---
async function resetSystem() {
    if(!confirm("Are you sure? This will delete ALL data.")) return;
    await fetch(`${API}/reset`, {method:'POST'});
    alert("System Reset Complete");
    location.reload();
}

function addRoomRow() {
    const div = document.createElement('div');
    div.className = 'input-row';
    div.innerHTML = `
        <input type="text" class="r-name" placeholder="Name">
        <input type="number" class="r-rows" placeholder="Rows" value="4">
        <input type="number" class="r-cols" placeholder="Cols" value="4">
    `;
    document.getElementById('room-inputs').appendChild(div);
}

async function saveRooms() {
    const rows = document.querySelectorAll('#room-inputs .input-row');
    let d = [];
    rows.forEach(r => {
        let n=r.querySelector('.r-name').value, rw=r.querySelector('.r-rows').value, cl=r.querySelector('.r-cols').value;
        if(n) d.push(`${n},${rw},${cl}`);
    });
    if(d.length==0) return alert("Enter Room Data");
    await fetch(`${API}/rooms/batch`, {method:'POST', body:d.join(';')});
    alert("Rooms Saved!");
    loadStats();
}

function addStudentRow() {
    const div = document.createElement('div');
    div.className = 'input-row';
    div.innerHTML = `
        <input type="text" class="s-br" placeholder="Branch">
        <input type="number" class="s-st" placeholder="Start">
        <input type="number" class="s-en" placeholder="End">
    `;
    document.getElementById('student-inputs').appendChild(div);
}

async function saveStudents() {
    const rows = document.querySelectorAll('#student-inputs .input-row');
    let d = [];
    rows.forEach(r => {
        let b=r.querySelector('.s-br').value, s=r.querySelector('.s-st').value, e=r.querySelector('.s-en').value;
        if(b) d.push(`${b},${s},${e}`);
    });
    if(d.length==0) return alert("Enter Class Data");
    await fetch(`${API}/students/batch`, {method:'POST', body:d.join(';')});
    alert("Batches Saved!");
    loadStats();
}

async function runAI() {
    const res = await fetch(`${API}/allocate`, {method:'POST'});
    const data = await res.json();
    if(data.status==='success') {
        alert("AI Allocation Complete!");
        showTab('map');
    } else {
        alert("Allocation Failed. Ensure capacity > students.");
    }
}

// --- API: MAP ---
async function loadMap() {
    const res = await fetch(`${API}/view`);
    const data = await res.json();
    const grid = document.getElementById('map-grid');
    grid.innerHTML = '';

    if(data.length === 0) {
        grid.innerHTML = '<div class="empty-state"><p>No data found.</p></div>';
        return;
    }

    // Sort by Room
    data.sort((a,b) => a.room.localeCompare(b.room) || a.row - b.row);

    data.forEach(s => {
        const div = document.createElement('div');
        div.className = 'seat-item';
        div.innerHTML = `
            <strong>${s.roll}</strong>
            <div class="tag">${s.branch}</div>
            <small>${s.room} (R${s.row}:C${s.col})</small>
        `;
        grid.appendChild(div);
    });
}
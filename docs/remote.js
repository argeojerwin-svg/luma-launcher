
  const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const sheetAnimations = new WeakMap();
  function showSheet(sheet) {
    stopInteractions();
    sheetAnimations.get(sheet)?.cancel(); sheet.removeAttribute('data-closing');
    if (!sheet.open) sheet.showModal();
    if (!reducedMotion()) {
      const animation = sheet.animate([{opacity:0,transform:'translateY(20px) scale(.985)'},{opacity:1,transform:'translateY(0) scale(1)'}], {duration:220,easing:'cubic-bezier(.2,.8,.2,1)'});
      sheetAnimations.set(sheet,animation);
    }
  }
  function closeSheet(sheet) {
    if (!sheet.open) return;
    sheetAnimations.get(sheet)?.cancel();
    if (reducedMotion()) { sheet.close(); return; }
    sheet.setAttribute('data-closing','');
    const animation = sheet.animate([{opacity:1,transform:'translateY(0) scale(1)'},{opacity:0,transform:'translateY(16px) scale(.99)'}], {duration:150,easing:'ease-in',fill:'forwards'});
    sheetAnimations.set(sheet,animation);
    animation.onfinish = () => {
      if (sheetAnimations.get(sheet) !== animation) return;
      sheet.close(); sheet.removeAttribute('data-closing'); animation.cancel(); sheetAnimations.delete(sheet);
    };
  }
  document.querySelectorAll('dialog').forEach(sheet => {
    sheet.addEventListener('cancel', event => { event.preventDefault(); closeSheet(sheet); });
    sheet.addEventListener('click', event => {
      if (event.target !== sheet) return;
      const r=sheet.getBoundingClientRect();
      if (event.clientX<r.left || event.clientX>r.right || event.clientY<r.top || event.clientY>r.bottom) closeSheet(sheet);
    });
  });
  const appsSheet = document.querySelector('#appsSheet');
  const appList = document.querySelector('#appList'), appSearch = document.querySelector('#appSearch'), appsStatus = document.querySelector('#appsStatus');
  let installedApps = [], appsBusy = false, appsLoadedAt = 0, appsLoading = null;
  async function loadApps() {
    if (appsLoading) return appsLoading;
    if (appsLoadedAt && Date.now() - appsLoadedAt < 30000) return;
    appsLoading = (async () => {
      const fresh = await (await request('/apps')).json();
      const changed = JSON.stringify(fresh) !== JSON.stringify(installedApps);
      installedApps = fresh; appsLoadedAt = Date.now();
      if (changed || !appList.childElementCount) renderApps();
    })();
    try { await appsLoading; } finally { appsLoading = null; }
  }
  function renderApps() {
    appList.replaceChildren();
    const matches = installedApps.filter(app => app.name.toLowerCase().includes(appSearch.value.trim().toLowerCase()));
    appsStatus.textContent = matches.length ? 'Choose an app to open on your TV' : 'No matching apps';
    matches.forEach(app => {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'app-launch';
      const initial = document.createElement('span'); initial.className = 'app-initial'; initial.setAttribute('aria-hidden', 'true'); initial.textContent = app.name.slice(0,1).toUpperCase();
      if (app.icon) {
        const icon = document.createElement('img'); icon.className = 'app-icon'; icon.alt = ''; icon.src = app.icon;
        icon.addEventListener('error', () => icon.replaceWith(initial), {once:true});
        button.append(icon);
      } else button.append(initial);
      button.append(document.createTextNode(app.name));
      button.addEventListener('click', async () => {
        if (appsBusy) return;
        appsBusy = true; appsStatus.textContent = 'Opening ' + app.name + '…';
        appList.querySelectorAll('button').forEach(b => b.disabled = true);
        try { await request('/launch', { method:'POST', body:app.id }); closeSheet(appsSheet); }
        catch (_) { appsStatus.textContent = 'Could not open this app. Try again.'; }
        finally { appsBusy = false; appList.querySelectorAll('button').forEach(b => b.disabled = false); }
      });
      appList.append(button);
      if (!reducedMotion() && appsSheet.open && appList.childElementCount <= 8) button.animate([{opacity:0,transform:'translateY(5px)'},{opacity:1,transform:'translateY(0)'}], {duration:150,delay:(appList.childElementCount-1)*15,easing:'ease-out',fill:'backwards'});
    });
  }
  async function openApps() {
    if (appsSheet.open) return;
    showSheet(appsSheet);
    appsStatus.textContent = 'Choose an app to open on your TV';
    if (appSearch.value) { appSearch.value = ''; renderApps(); }
    if (!appsLoadedAt) appsStatus.textContent = 'Loading apps…';
    try { await loadApps(); }
    catch (_) { if (!appsLoadedAt) appsStatus.textContent = 'Could not load apps. Close and try again.'; }
  }
  document.querySelector('#closeApps').addEventListener('click', () => closeSheet(appsSheet));
  appSearch.addEventListener('input', renderApps);
  const typingSheet = document.querySelector('#typingSheet');
  document.querySelector('#openTyping').addEventListener('click', () => { showSheet(typingSheet); if (!input.disabled) input.focus(); });
  document.querySelector('#closeTyping').addEventListener('click', () => closeSheet(typingSheet));
  const navMode = document.querySelector('#navMode'), touchMode = document.querySelector('#touchMode');
  let modeAnimation;
  function setMode(touch) {
    if (document.querySelector('.pad').hidden === touch) return;
    stopInteractions();
    modeAnimation?.cancel();
    document.querySelector('.pad').hidden = touch;
    document.querySelector('#touchpad').hidden = !touch;
    document.querySelector('#scrollControls').hidden = !touch;
    navMode.setAttribute('aria-pressed', String(!touch)); touchMode.setAttribute('aria-pressed', String(touch));
    if (!reducedMotion()) modeAnimation = document.querySelector(touch ? '#touchpad' : '.pad').animate([{opacity:.3,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}], {duration:180,easing:'cubic-bezier(.2,.8,.2,1)'});
  }
  navMode.addEventListener('click', () => setMode(false));
  touchMode.addEventListener('click', () => setMode(true));
  const pointerOption = document.querySelector('#crossAppPointer');
  pointerOption.addEventListener('change', async () => {
    pointerOption.disabled = true;
    try { const state = await (await request('/pointer', {method:'POST',body:pointerOption.checked?'on':'off'})).json(); pointerOption.checked = state.enabled; }
    catch (_) { pointerOption.checked = !pointerOption.checked; }
    finally { pointerOption.disabled = false; }
  });
  let token = '', projectorOrigin = '';
  function loadPairing(value) {
    try {
      const url = new URL(value.trim()), parts = url.hostname.split('.').map(Number);
      const privateIP = parts.length === 4 && parts.every(n => Number.isInteger(n) && n >= 0 && n <= 255) &&
        (parts[0] === 10 || parts[0] === 192 && parts[1] === 168 || parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31);
      if (url.protocol !== 'http:' || !privateIP || url.port !== '19767' || url.username || url.password || url.pathname !== '/' || url.search || !/^#[a-f0-9]{48}$/.test(url.hash)) return false;
      projectorOrigin = url.origin; token = url.hash.slice(1); return true;
    } catch (_) { return false; }
  }
  try { loadPairing(localStorage.getItem('luma-projector') || ''); } catch (_) {}

  const input = document.querySelector('#text');
  const send = document.querySelector('#send');
  const status = document.querySelector('#status');
  let pending = null, sending = false, connected = false, submitting = false, done = false, timer;
  const message = (text, error = false) => { status.textContent = text; status.className = error ? 'error' : ''; };
  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      if (!projectorOrigin || !token) throw new Error('Paste your private projector link and tap Connect.');
      const response = await fetch(projectorOrigin + path, { ...options, mode: 'cors', credentials: 'omit', targetAddressSpace: 'local', signal: controller.signal, cache: 'no-store', headers: { 'X-Luma-Token': token, ...(options.headers || {}) } });
      const body = await response.text();
      if (!response.ok) {
        const error = new Error(response.status === 403 || response.status === 410 ? 'Scan the current QR code on your TV to reconnect.' : 'Could not send. Keep this text window open on the TV and try again.');
        error.status = response.status; throw error;
      }
      return { json: async () => JSON.parse(body) };
    } finally { clearTimeout(timeout); }
  }
  async function flush() {
    if (sending || pending === null) return;
    sending = true;
    try {
      while (pending !== null) {
        const edit = pending; pending = null;
        try {
          await request(edit.submit ? '/submit' : '/text', { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=UTF-8' }, body: edit.value });
          if (edit.submit) {
            done = true; send.textContent = 'Done';
            message('Text sent. You can keep using the remote below.');
            return;
          }
          if (!submitting) message('Text is on your TV. Tap Go when finished.');
        } catch (error) {
          if (edit.submit) {
            // A lost response may still have submitted. Do not automatically submit twice.
            send.textContent = 'Check your TV';
            message('Could not confirm. Check your TV; scan its current QR code to try again.', true);
            return;
          }
          if (pending && pending.submit) continue;
          if (pending === null) pending = edit;
          throw error;
        }
      }
    } catch (error) { message(error.message || 'Connection lost. Check Wi-Fi and scan the QR code again.', true); }
    finally { sending = false; }
  }
  async function connect() {
    try {
      if (!token) throw new Error('Scan the QR code beside the keyboard on your TV.');
      const state = await (await request('/state')).json();
      document.querySelector('#label').textContent = state.title;
      input.maxLength = state.limit; input.value = state.text;
      input.disabled = false; send.disabled = false; send.textContent = 'Go'; connected = true;
      updateRemote(state);
      if (state.permanent) request('/pointer').then(r => r.json()).then(p => {pointerOption.checked = p.enabled; pointerOption.disabled = false;}).catch(() => {});
      if (!appsLoadedAt && state.remote) setTimeout(() => loadApps().catch(() => {}), 500);
      if (state.permanent) document.querySelector('#help').textContent = 'Save this page to your phone’s Home Screen. It reconnects automatically on the same Wi-Fi while the projector is on. Open a text field on the projector to type. Keep this private link to yourself.';
      if (state.textEnabled) message('Connected. Start typing.');
    } catch (error) { send.disabled = false; send.textContent = 'Retry connection'; message(error.message || 'Could not connect. Check that your phone and projector use the same Wi-Fi.', true); }
  }
  input.addEventListener('input', () => {
    if (submitting || done) return;
    pending = { value: input.value, submit: false }; clearTimeout(timer); timer = setTimeout(flush, 40);
  });
  document.querySelector('#form').addEventListener('submit', async event => {
    event.preventDefault();
    if (submitting || done) return;
    if (!connected) { if (token) await connect(); return; }
    if (!input.value.trim()) { message('Enter some text first.', true); input.focus(); return; }
    submitting = true; input.disabled = true; send.disabled = true; send.textContent = 'Opening…';
    pending = { value: input.value, submit: true }; clearTimeout(timer); message('Confirming on your TV…'); flush();
  });
  window.addEventListener('pagehide', () => clearTimeout(timer));
  const remoteStatus = document.querySelector('#remoteStatus');
  const remoteButtons = document.querySelectorAll('[data-command]');
  let remoteAvailable = false, remoteReady = false, remoteBusy = false, movePending = null, stopped = false, lastControlMessage = "";
  let commandQueue = Promise.resolve();
  let lastEditor = "";
  function updateRemote(state) {
    if (Object.prototype.hasOwnProperty.call(state, 'target')) setMagneticTarget(state.target);
    if (state.editor !== lastEditor) {
      lastEditor = state.editor; done = false; submitting = false; pending = null; clearTimeout(timer);
      input.value = state.text; input.maxLength = state.limit;
      document.querySelector('#label').textContent = state.title;
    }
    if (state.textEnabled && !submitting) { input.disabled = false; send.disabled = false; send.textContent = 'Go'; }

    remoteAvailable = !!state.remote;
    remoteReady = remoteAvailable && state.inputReady !== false;
    remoteButtons.forEach(button => button.disabled = !(remoteReady || remoteAvailable && ['apps','home','controlCentre'].includes(button.dataset.command)));
    setHealth(remoteReady ? 'ready' : 'waiting');
    if (!remoteReady) stopInteractions();
    if (!state.textEnabled) { input.disabled = true; send.disabled = true; message('Open a text field on the projector to type. Remote controls are available below.'); }
  }
  function setHealth(health) {
    remoteStatus.dataset.health = health;
    remoteStatus.textContent = health === 'ready' ? '● Connected · TV controls ready' : health === 'waiting' ? '● Connected · TV controls reconnecting' : '● Offline · reconnecting…';
  }
  function control(command, allowed = () => true) {
    commandQueue = commandQueue.then(async () => {
      if (!remoteAvailable || stopped || !allowed()) return;
      if (!remoteReady && !['home','controlCentre','appSwitcher'].includes(command)) return;
      try {
        const response = await request('/remote', { method: 'POST', body: command });
        if (command.startsWith('move:')) setMagneticTarget((await response.json()).target);
        else if (command.startsWith('scroll')) setMagneticTarget(null);
        lastControlMessage = command.startsWith('move:') ? 'Touchpad connected' : 'Sent · ' + command.split(':')[0];
      } catch (error) {
        remoteReady = false; stopInteractions();
        if (error.status === 409) setHealth('waiting');
        else { connected = false; remoteAvailable = false; setHealth('offline'); }
        return;
      }
      setHealth(remoteReady ? 'ready' : 'waiting');
    });
    return commandQueue;
  }
  remoteButtons.forEach(button => button.addEventListener('click', event => {
    if (button.dataset.command === 'apps') { openApps(); return; }
    if (button.dataset.command === 'controlCentre') { if (event.detail === 0) control('controlCentre'); return; }
    if (['scrollUp','scrollDown','up','down','left','right','volumeUp','volumeDown'].includes(button.dataset.command)) return;
    if (button.dataset.command === 'power' && !confirm('Put the projector in standby? Use its physical remote to wake it.')) return;
    control(button.dataset.command);
  }));
  const centreButton = document.querySelector('[data-command="controlCentre"]');
  let centreHeld = false, centreLong = false, centreTimer;
  centreButton.addEventListener('pointerdown', event => {
    if (!remoteAvailable || centreHeld) return;
    centreHeld = true; centreLong = false; centreButton.setPointerCapture(event.pointerId);
    centreTimer = setTimeout(() => { if (centreHeld) { centreLong = true; control('appSwitcher'); } }, 500);
  });
  centreButton.addEventListener('pointerup', () => {
    if (!centreHeld) return;
    centreHeld = false; clearTimeout(centreTimer);
    if (!centreLong) control('controlCentre');
  });
  const cancelCentre = () => { centreHeld = false; clearTimeout(centreTimer); };
  centreButton.addEventListener('pointercancel', cancelCentre);
  centreButton.addEventListener('lostpointercapture', cancelCentre);
  centreButton.addEventListener('contextmenu', event => event.preventDefault());
  const repeatStops = [];
  function bindRepeat(button) {
    let held = false, repeatTimer, sequence = 0;
    const command = button.dataset.command;
    const interval = command.startsWith('scroll') ? 95 : command.startsWith('volume') ? 110 : 80;
    const stop = () => { held = false; sequence++; clearTimeout(repeatTimer); };
    repeatStops.push(stop);
    button.addEventListener('pointerdown', event => {
      if (!remoteReady || held) return;
      held = true; const token = ++sequence; button.setPointerCapture(event.pointerId);
      const repeat = () => {
        if (!held || token !== sequence || !remoteReady) return;
        control(command, () => held && token === sequence).then(() => {
          if (held && token === sequence && remoteReady) repeatTimer = setTimeout(repeat, interval);
        });
      };
      control(command).then(() => {
        if (held && token === sequence && remoteReady) repeatTimer = setTimeout(repeat, command.startsWith('scroll') ? 240 : 320);
      });
    });
    button.addEventListener('pointerup', stop);
    button.addEventListener('pointercancel', stop);
    button.addEventListener('lostpointercapture', stop);
    button.addEventListener('contextmenu', event => event.preventDefault());
    button.addEventListener('click', event => { if (event.detail === 0) control(command); });
  }
  document.querySelectorAll('[data-command="up"], [data-command="down"], [data-command="left"], [data-command="right"], [data-command="volumeUp"], [data-command="volumeDown"], [data-command="scrollUp"], [data-command="scrollDown"]').forEach(bindRepeat);
  const touchpad = document.querySelector('#touchpad');
  let pointer = null, lastX = 0, lastY = 0, distance = 0, x = 500, y = 500;
  let magnet = null, releasedMagnet = '', intentX = 500, intentY = 500;
  function setMagneticTarget(rect) {
    if (!Array.isArray(rect) || rect.length !== 4 || !rect.every(Number.isFinite) || rect[2] <= rect[0] || rect[3] <= rect[1]) {
      magnet = null; releasedMagnet = ''; intentX = x; intentY = y; return;
    }
    const key = rect.join(',');
    if (key === releasedMagnet || x < rect[0]-1 || x > rect[2]+1 || y < rect[1]-1 || y > rect[3]+1) return;
    if (!magnet || magnet.key !== key) { intentX = x; intentY = y; }
    magnet = {rect, key};
  }
  function movePointer(dx, dy) {
    intentX = Math.max(0,Math.min(1000,intentX+dx)); intentY = Math.max(0,Math.min(1000,intentY+dy));
    if (!magnet) { x = intentX; y = intentY; return; }
    const [left,top,right,bottom] = magnet.rect;
    const l = left+.7, t = top+.7, r = right-.7, b = bottom-.7;
    const cx = Math.max(l,Math.min(r,intentX)), cy = Math.max(t,Math.min(b,intentY));
    const ox = intentX-cx, oy = intentY-cy;
    const releaseX = Math.min(12,Math.max(5,(right-left)*.12));
    const releaseY = Math.min(18,Math.max(6,(bottom-top)*.12));
    if (Math.abs(ox) > releaseX || Math.abs(oy) > releaseY) {
      x = cx+Math.sign(ox)*Math.max(0,Math.abs(ox)-releaseX);
      y = cy+Math.sign(oy)*Math.max(0,Math.abs(oy)-releaseY);
      releasedMagnet = magnet.key; magnet = null; intentX = x; intentY = y;
    } else { x = cx; y = cy; }
  }
  const contacts = new Map();
  let gesture = 'idle', pointerEpoch = 0, scrollEpoch = 0, scrollY = 0, scrollCarry = 0, scrollPending = 0, scrolling = false;
  const centroidY = () => Array.from(contacts.values()).reduce((total,p) => total+p.y,0)/contacts.size;
  function flushScroll() {
    if (scrolling || !scrollPending || gesture !== 'scroll' || !remoteReady) return;
    scrolling = true;
    const amount = Math.max(-4,Math.min(4,scrollPending)); scrollPending -= amount;
    const token = scrollEpoch;
    control('scroll:'+amount, () => token === scrollEpoch && gesture === 'scroll').then(() => {
      scrolling = false;
      flushScroll();
    });
  }
  touchpad.addEventListener('pointerdown', event => {
    if (!remoteReady || contacts.size >= 2) return;
    contacts.set(event.pointerId, {x:event.clientX,y:event.clientY}); touchpad.setPointerCapture(event.pointerId);
    if (contacts.size === 1) {
      gesture = 'pointer'; pointerEpoch++; pointer = event.pointerId;
      intentX = x; intentY = y; lastX = event.clientX; lastY = event.clientY; distance = 0;
    } else {
      gesture = 'scroll'; pointerEpoch++; movePending = null;
      scrollEpoch++; scrollY = centroidY(); scrollCarry = 0; scrollPending = 0;
    }
  });
  touchpad.addEventListener('pointermove', event => {
    if (!contacts.has(event.pointerId)) return;
    contacts.set(event.pointerId, {x:event.clientX,y:event.clientY});
    if (gesture === 'scroll' && contacts.size === 2) {
      const nextY = centroidY(); scrollCarry += nextY-scrollY; scrollY = nextY;
      const steps = Math.trunc(scrollCarry/16);
      if (steps) { scrollCarry -= steps*16; scrollPending = Math.max(-12,Math.min(12,scrollPending+steps)); flushScroll(); }
      return;
    }
    if (gesture !== 'pointer' || event.pointerId !== pointer) return;
    const dx = event.clientX-lastX, dy = event.clientY-lastY;
    lastX = event.clientX; lastY = event.clientY; distance += Math.abs(dx)+Math.abs(dy);
    movePointer(dx*2, dy*3);
    movePending = `move:${Math.round(x)},${Math.round(y)}`; flushMove();
  });
  async function flushMove() {
    if (remoteBusy || !movePending) return;
    remoteBusy = true;
    try {
      while (movePending) {
        const command = movePending, token = pointerEpoch; movePending = null;
        await control(command, () => token === pointerEpoch);
      }
    } finally { remoteBusy = false; }
  }
  function endContact(event, cancelled) {
    if (!contacts.has(event.pointerId)) return;
    const tap = !cancelled && gesture === 'pointer' && contacts.size === 1 && distance < 8;
    contacts.delete(event.pointerId);
    if (gesture === 'scroll') { scrollEpoch++; scrollPending = 0; scrollCarry = 0; }
    gesture = contacts.size ? 'blocked' : 'idle'; pointer = null;
    if (tap) control(`tap:${Math.round(x)},${Math.round(y)}`);
  }
  touchpad.addEventListener('pointerup', event => endContact(event,false));
  touchpad.addEventListener('pointercancel', event => endContact(event,true));
  touchpad.addEventListener('lostpointercapture', event => endContact(event,true));
  touchpad.addEventListener('contextmenu', event => event.preventDefault());
  function stopInteractions() {
    repeatStops.forEach(stop => stop()); cancelCentre();
    contacts.clear(); gesture = 'idle'; pointer = null;
    pointerEpoch++; scrollEpoch++; movePending = null; scrollPending = 0; scrollCarry = 0;
  }
  window.addEventListener('blur', stopInteractions);
  window.addEventListener('pagehide', stopInteractions);
  document.addEventListener('visibilitychange', () => { if (document.hidden) stopInteractions(); });
  document.querySelector('#disconnect').addEventListener('click', async () => {
    try { await request('/disconnect', { method: 'POST', body: '' }); } catch (_) {}
    stopped = true; remoteReady = false; remoteAvailable = false; stopInteractions(); remoteButtons.forEach(button => button.disabled = true);
    input.disabled = true; send.disabled = true; remoteStatus.textContent = 'Disconnected. Scan a new QR code to reconnect.';
  });
  let checkingState = false;
  const stateTimer = setInterval(async () => {
    if (stopped || checkingState) return;
    checkingState = true;
    try {
    if (!connected) { if (token) await connect(); return; }
    try { updateRemote(await (await request('/state')).json()); }
    catch (_) { remoteReady = false; remoteAvailable = false; stopInteractions(); remoteButtons.forEach(button => button.disabled = true); connected = false; setHealth('offline'); }
    } finally { checkingState = false; }
  }, 2500);
  if (token) connect();

  document.querySelector('#pair').addEventListener('click', () => {
    const value = document.querySelector('#pairLink').value.trim();
    if (!loadPairing(value)) { document.querySelector('#pairStatus').textContent = 'Use the complete local QR link: http://projector-ip:19767/#private-token'; return; }
    try { localStorage.setItem('luma-projector', value); } catch (_) {}
    document.querySelector('#pairLink').value = '';
    stopInteractions(); remoteAvailable = false; appsLoadedAt = 0;
    connected = false; stopped = false; done = false; submitting = false; pending = null; lastEditor = ''; clearTimeout(timer);
    document.querySelector('#pairStatus').textContent = 'Saved on this phone. Allow local-network access when prompted.';
    connect();
  });
  document.querySelector('#forget').addEventListener('click', () => {
    if (!confirm('Forget this phone’s saved projector link?')) return;
    try { localStorage.removeItem('luma-projector'); } catch (_) {}
    stopInteractions(); remoteAvailable = false;
    token = ''; projectorOrigin = ''; stopped = true; connected = false; remoteReady = false;
    remoteButtons.forEach(button => button.disabled = true); input.disabled = true; send.disabled = true;
    document.querySelector('#pairLink').value = ''; document.querySelector('#pairStatus').textContent = 'Projector link removed from this phone.';
  });
  let installPrompt;
  window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); installPrompt = event; document.querySelector('#install').hidden = false; });
  document.querySelector('#install').addEventListener('click', async () => {
    if (!installPrompt) return; await installPrompt.prompt(); installPrompt = null; document.querySelector('#install').hidden = true;
  });
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  if (!token) document.querySelector('.help-panel').open = true;

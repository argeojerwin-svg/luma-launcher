
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
      if (!response.ok) throw new Error(response.status === 403 || response.status === 410 ? 'Scan the current QR code on your TV to reconnect.' : 'Could not send. Keep this text window open on the TV and try again.');
      return response;
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
      if (state.permanent) document.querySelector('#help').textContent = 'Save this page to your phone’s Home Screen. It reconnects automatically on the same Wi-Fi while the projector is on. Open a text field on the projector to type. Keep this private link to yourself.';
      if (state.textEnabled) message('Connected. Start typing.');
    } catch (error) { send.disabled = false; send.textContent = 'Retry connection'; message(error.message || 'Could not connect. Check that your phone and projector use the same Wi-Fi.', true); }
  }
  input.addEventListener('input', () => {
    if (submitting || done) return;
    pending = { value: input.value, submit: false }; clearTimeout(timer); timer = setTimeout(flush, 40);
  });
  document.querySelector('#form').addEventListener('submit', event => {
    event.preventDefault();
    if (submitting || done) return;
    if (!connected) { if (token) connect(); return; }
    if (!input.value.trim()) { message('Enter some text first.', true); input.focus(); return; }
    submitting = true; input.disabled = true; send.disabled = true; send.textContent = 'Opening…';
    pending = { value: input.value, submit: true }; clearTimeout(timer); message('Confirming on your TV…'); flush();
  });
  window.addEventListener('pagehide', () => clearTimeout(timer));
  const remoteStatus = document.querySelector('#remoteStatus');
  const remoteButtons = document.querySelectorAll('[data-command]');
  let remoteReady = false, remoteBusy = false, movePending = null, stopped = false, lastControlMessage = "";
  let commandQueue = Promise.resolve();
  let lastEditor = "";
  function updateRemote(state) {
    if (state.editor !== lastEditor) {
      lastEditor = state.editor; done = false; submitting = false; pending = null; clearTimeout(timer);
      input.value = state.text; input.maxLength = state.limit;
      document.querySelector('#label').textContent = state.title;
    }
    if (state.textEnabled && !submitting) { input.disabled = false; send.disabled = false; send.textContent = 'Go'; }

    remoteReady = !!state.remote;
    remoteButtons.forEach(button => button.disabled = !remoteReady);
    remoteStatus.textContent = remoteReady ? (lastControlMessage || 'Connected · arrows or touchpad') : 'Phone remote is unavailable. Text entry still works.';
    if (!state.textEnabled) { input.disabled = true; send.disabled = true; message('Open a text field on the projector to type. Remote controls are available below.'); }
  }
  function control(command) {
    commandQueue = commandQueue.then(async () => {
      if (!remoteReady || stopped) return;
      try {
        await request('/remote', { method: 'POST', body: command });
        lastControlMessage = command.startsWith('move:') ? 'Touchpad connected' : 'Sent · ' + command.split(':')[0];
      } catch (error) { lastControlMessage = error.message; }
      remoteStatus.textContent = lastControlMessage;
    });
    return commandQueue;
  }
  remoteButtons.forEach(button => button.addEventListener('click', () => {
    if (button.dataset.command === 'controlCentre') return;
    if (button.dataset.command === 'scrollUp' || button.dataset.command === 'scrollDown') return;
    if (button.dataset.command === 'power' && !confirm('Put the projector in standby? Use its physical remote to wake it.')) return;
    control(button.dataset.command);
  }));
  const centreButton = document.querySelector('#controlCentre');
  let centrePointer = null, centreTimer, centreHeld = false, centreStartX = 0, centreStartY = 0;
  function cancelCentre() { clearTimeout(centreTimer); centrePointer = null; }
  centreButton.addEventListener('contextmenu', event => event.preventDefault());
  centreButton.addEventListener('pointerdown', event => {
    if (!remoteReady || stopped || centrePointer !== null || !event.isPrimary || event.button !== 0) return;
    centrePointer = event.pointerId; centreHeld = false;
    centreStartX = event.clientX; centreStartY = event.clientY;
    centreButton.setPointerCapture(event.pointerId);
    centreTimer = setTimeout(() => {
      if (centrePointer === event.pointerId) { centreHeld = true; control('appSwitcher'); }
    }, 550);
  });
  centreButton.addEventListener('pointermove', event => {
    if (centrePointer === event.pointerId && Math.hypot(event.clientX-centreStartX, event.clientY-centreStartY) > 12) cancelCentre();
  });
  centreButton.addEventListener('pointerup', event => {
    if (centrePointer !== event.pointerId) return;
    const held = centreHeld; cancelCentre();
    if (!held) control('controlCentre');
  });
  centreButton.addEventListener('pointercancel', cancelCentre);
  centreButton.addEventListener('lostpointercapture', cancelCentre);
  centreButton.addEventListener('click', event => { if (event.detail === 0) control('controlCentre'); });
  document.querySelectorAll('[data-command="scrollUp"], [data-command="scrollDown"]').forEach(button => {
    let held = false, repeatTimer;
    const repeat = async () => {
      if (!held) return;
      await control(button.dataset.command);
      if (held) repeatTimer = setTimeout(repeat, 95);
    };
    button.addEventListener('pointerdown', event => {
      if (!remoteReady || held) return;
      held = true; button.setPointerCapture(event.pointerId);
      control(button.dataset.command).then(() => {
        if (held) repeatTimer = setTimeout(repeat, 240);
      });
    });
    const stop = () => { held = false; clearTimeout(repeatTimer); };
    button.addEventListener('pointerup', stop);
    button.addEventListener('pointercancel', stop);
    button.addEventListener('lostpointercapture', stop);
    button.addEventListener('click', event => { if (event.detail === 0) control(button.dataset.command); });
  });
  const touchpad = document.querySelector('#touchpad');
  let pointer = null, lastX = 0, lastY = 0, distance = 0, x = 500, y = 500;
  touchpad.addEventListener('pointerdown', event => {
    if (!remoteReady || pointer !== null) return;
    pointer = event.pointerId; touchpad.setPointerCapture(pointer); lastX = event.clientX; lastY = event.clientY; distance = 0;
  });
  touchpad.addEventListener('pointermove', event => {
    if (event.pointerId !== pointer) return;
    const dx = event.clientX - lastX, dy = event.clientY - lastY;
    lastX = event.clientX; lastY = event.clientY; distance += Math.abs(dx) + Math.abs(dy);
    x = Math.max(0, Math.min(1000, x + dx * 2)); y = Math.max(0, Math.min(1000, y + dy * 3));
    movePending = `move:${Math.round(x)},${Math.round(y)}`; flushMove();
  });
  async function flushMove() {
    if (remoteBusy || !movePending) return;
    remoteBusy = true;
    while (movePending) { const command = movePending; movePending = null; await control(command); }
    remoteBusy = false;
  }
  touchpad.addEventListener('pointerup', event => {
    if (event.pointerId !== pointer) return;
    pointer = null;
    if (distance < 8) control(`tap:${Math.round(x)},${Math.round(y)}`);
  });
  touchpad.addEventListener('pointercancel', () => { pointer = null; });
  document.querySelector('#disconnect').addEventListener('click', async () => {
    try { await request('/disconnect', { method: 'POST', body: '' }); } catch (_) {}
    stopped = true; remoteReady = false; remoteButtons.forEach(button => button.disabled = true);
    input.disabled = true; send.disabled = true; remoteStatus.textContent = 'Disconnected. Scan a new QR code to reconnect.';
  });
  const stateTimer = setInterval(async () => {
    if (stopped) return;
    if (!connected) { if (token) connect(); return; }
    try { updateRemote(await (await request('/state')).json()); }
    catch (_) { remoteReady = false; remoteButtons.forEach(button => button.disabled = true); connected = false; remoteStatus.textContent = 'Reconnecting… Check the projector is on and both devices use the same Wi-Fi.'; }
  }, 2500);
  if (token) connect();

  document.querySelector('#pair').addEventListener('click', () => {
    const value = document.querySelector('#pairLink').value.trim();
    if (!loadPairing(value)) { document.querySelector('#pairStatus').textContent = 'Use the complete local QR link: http://projector-ip:19767/#private-token'; return; }
    try { localStorage.setItem('luma-projector', value); } catch (_) {}
    document.querySelector('#pairLink').value = '';
    connected = false; stopped = false; done = false; submitting = false; pending = null; lastEditor = ''; clearTimeout(timer);
    document.querySelector('#pairStatus').textContent = 'Saved on this phone. Allow local-network access when prompted.';
    connect();
  });
  document.querySelector('#forget').addEventListener('click', () => {
    if (!confirm('Forget this phone’s saved projector link?')) return;
    try { localStorage.removeItem('luma-projector'); } catch (_) {}
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

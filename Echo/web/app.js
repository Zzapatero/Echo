(() => {
  'use strict';

  const channels = [
    { name: 'General', icon: '✳', note: 'Conversaciones de todos' },
    { name: 'Fútbol', icon: '◉', note: 'El deporte que nos une' },
    { name: 'Inteligencia Artificial', icon: '✦', note: 'Ideas sobre IA y futuro' },
    { name: 'Noticias', icon: '▤', note: 'Lo que está pasando' },
    { name: 'Actualidad', icon: '◷', note: 'Temas del momento' },
    { name: 'Tecnología', icon: '⌘', note: 'Productos, código e innovación' }
  ];

  const state = {
    sessionId: null,
    mode: 'login',
    username: '',
    authenticated: false,
    resetRequired: false,
    activeKey: null,
    activeType: null,
    activeName: null,
    people: [],
    messages: new Map(),
    pendingPrivate: new Map(),
    polling: false,
    toastTimer: null
  };

  const byId = id => document.getElementById(id);
  const authScreen = byId('authScreen');
  const chatApp = byId('chatApp');
  const authForm = byId('authForm');
  const authNotice = byId('authNotice');
  const resetNotice = byId('resetNotice');
  const messageList = byId('messageList');
  const conversationList = byId('conversationList');

  async function createSession() {
    const response = await fetch('/api/session', { method: 'POST', cache: 'no-store' });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'No se pudo conectar con Echo.');
    state.sessionId = data.id;
    sessionStorage.setItem('echo-session', data.id);
  }

  async function ensureSession() {
    const saved = sessionStorage.getItem('echo-session');
    if (saved) {
      try { await fetch(`/api/session?id=${encodeURIComponent(saved)}`, { method: 'DELETE', cache: 'no-store' }); }
      catch (_) { /* La sesión anterior pudo cerrarse al detener el servidor. */ }
      sessionStorage.removeItem('echo-session');
    }
    await createSession();
  }

  async function sendCommand(command) {
    if (!state.sessionId) throw new Error('No hay una sesión de Echo abierta.');
    const body = new URLSearchParams({ id: state.sessionId, command });
    const response = await fetch('/api/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body,
      cache: 'no-store'
    });
    const data = await response.json();
    if (response.status === 404 || response.status === 410) {
      sessionStorage.removeItem('echo-session');
      state.sessionId = null;
      state.authenticated = false;
      chatApp.classList.add('hidden');
      authScreen.classList.remove('hidden');
      void createSession().catch(error => setNotice(authNotice, error.message, true));
    }
    if (!response.ok) throw new Error(data.error || 'No se pudo enviar la acción.');
  }

  function setNotice(element, message, isError = false) {
    element.textContent = message || '';
    element.classList.toggle('error', Boolean(isError));
  }

  function setMode(mode) {
    state.mode = mode;
    const registering = mode === 'register';
    byId('loginTab').classList.toggle('active', !registering);
    byId('registerTab').classList.toggle('active', registering);
    byId('loginTab').setAttribute('aria-selected', String(!registering));
    byId('registerTab').setAttribute('aria-selected', String(registering));
    byId('authTitle').textContent = registering ? 'Empieza por aquí.' : 'Qué bueno verte.';
    byId('authSubtitle').textContent = registering
      ? 'Crea tu cuenta y encuentra tu conversación.'
      : 'Entra y retoma la conversación donde la dejaste.';
    byId('authSubmit').querySelector('span').textContent = registering ? 'Crear mi cuenta' : 'Entrar a Echo';
    byId('password').autocomplete = registering ? 'new-password' : 'current-password';
    byId('passwordHelp').textContent = registering ? 'Usa una contraseña de al menos 12 caracteres.' : 'Tus conversaciones están a un clic.';
    setNotice(authNotice, '');
  }

  async function onAuthSubmit(event) {
    event.preventDefault();
    const username = byId('username').value.trim();
    const password = byId('password').value;
    if (!username || !password) return;
    if (state.mode === 'register' && password.length < 12) {
      setNotice(authNotice, 'La contraseña debe tener al menos 12 caracteres.', true);
      return;
    }
    if (/[|\r\n]/.test(username) || (state.mode === 'register' && /[,|\r\n]/.test(password))) {
      setNotice(authNotice, 'El nombre o la contraseña contiene un carácter no permitido.', true);
      return;
    }
    state.username = username;
    byId('authSubmit').disabled = true;
    setNotice(authNotice, state.mode === 'register' ? 'Creando tu cuenta…' : 'Conectando…');
    try {
      await sendCommand(`${state.mode === 'register' ? 'REG' : 'LOGIN'}|${username}|${password}`);
    } catch (error) {
      setNotice(authNotice, error.message, true);
    } finally {
      byId('authSubmit').disabled = false;
    }
  }

  async function onResetSubmit(event) {
    event.preventDefault();
    const password = byId('newPassword').value;
    if (password.length < 12 || /[,|\r\n]/.test(password)) {
      setNotice(resetNotice, 'Usa al menos 12 caracteres y evita comas o saltos de línea.', true);
      return;
    }
    try {
      await sendCommand(`CHANGE_PASSWORD|${password}`);
      setNotice(resetNotice, 'Guardando contraseña…');
    } catch (error) {
      setNotice(resetNotice, error.message, true);
    }
  }

  async function pollEvents() {
    if (state.polling || !state.sessionId) return;
    state.polling = true;
    try {
      const response = await fetch(`/api/events?id=${encodeURIComponent(state.sessionId)}`, { cache: 'no-store' });
      if (response.status === 404) {
        sessionStorage.removeItem('echo-session');
        state.sessionId = null;
        state.authenticated = false;
        chatApp.classList.add('hidden');
        authScreen.classList.remove('hidden');
        await createSession();
        setNotice(authNotice, 'La sesión anterior terminó. Vuelve a iniciar sesión.', true);
        return;
      }
      const data = await response.json();
      for (const event of data.events || []) handleEvent(event);
      if (!data.connected && state.authenticated) handleEvent('CONNECTION_CLOSED|');
    } catch (_) {
      if (state.authenticated) showToast('Intentando recuperar la conexión…', true);
    } finally {
      state.polling = false;
    }
  }

  function decodeBase64Url(value) {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(base64), character => character.charCodeAt(0));
    return new TextDecoder('utf-8').decode(bytes);
  }

  function handleEvent(frame) {
    const parts = frame.split('|');
    const command = parts[0];
    if (command === 'LOGIN_OK') {
      state.authenticated = true;
      state.resetRequired = parts[1] === 'RESET_REQUIRED';
      if (state.resetRequired) {
        authForm.classList.add('hidden');
        byId('authTabs').classList.add('hidden');
        byId('authTitle').classList.add('hidden');
        byId('authSubtitle').classList.add('hidden');
        byId('authNotice').classList.add('hidden');
        byId('authFootnote').classList.add('hidden');
        byId('resetForm').classList.remove('hidden');
        byId('newPassword').focus();
      } else {
        enterChat();
      }
      return;
    }
    if (command === 'PASSWORD_UPDATED') {
      state.resetRequired = false;
      enterChat();
      showToast('Contraseña actualizada. Ya puedes usar Echo.');
      return;
    }
    if (command === 'LOGIN_ERR') {
      setNotice(authNotice, 'No se pudo iniciar sesión. Revisa el nombre y la contraseña.', true);
      return;
    }
    if (command === 'REG_OK') {
      byId('password').value = '';
      setMode('login');
      setNotice(authNotice, 'Cuenta creada. Ya puedes iniciar sesión.');
      return;
    }
    if (command === 'REG_ERR') {
      setNotice(authNotice, parts.slice(1).join('|').replace(/_/g, ' '), true);
      return;
    }
    if (command === 'USERS_BEGIN') {
      state.people = [];
      return;
    }
    if (command === 'USER' && parts.length >= 3) {
      try {
        const name = decodeBase64Url(parts[1]);
        if (name !== state.username) state.people.push({ name, online: parts[2] === 'ONLINE' });
      } catch (_) { /* Ignora entradas malformadas del directorio. */ }
      return;
    }
    if (command === 'USERS_END') {
      state.people.sort((a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name, 'es'));
      renderDirectory();
      refreshActivePresence();
      return;
    }
    if (command === 'ROOM_BROADCAST' && parts.length >= 5) {
      appendMessage(`room:${parts[1]}`, {
        author: parts[2], time: parts[3], text: parts.slice(4).join('|'), mine: parts[2] === state.username
      });
      return;
    }
    if (command === 'HIST_DATA' && parts.length >= 5) {
      appendMessage(`room:${parts[1]}`, {
        author: parts[2], time: parts[3], text: parts.slice(4).join('|'), mine: parts[2] === state.username, history: true
      });
      return;
    }
    if (command === 'RECV_PRIV' && parts.length >= 5) {
      try {
        const content = new TextDecoder('utf-8').decode(Uint8Array.from(atob(parts[4]), character => character.charCodeAt(0)));
        appendMessage(`person:${parts[2]}`, { author: parts[2], time: parts[3], text: content, mine: false, id: parts[1] });
        void sendCommand(`ACK_PRIV|${parts[1]}`).catch(() => {});
        if (state.activeKey !== `person:${parts[2]}`) showToast(`Nuevo mensaje de ${parts[2]}`);
      } catch (_) {
        showToast('No se pudo leer un mensaje privado.', true);
      }
      return;
    }
    if (command === 'NOTIFY_JOIN' || command === 'NOTIFY_LEAVE') {
      void sendCommand('LIST_USERS').catch(() => {});
      return;
    }
    if (command === 'PRIV_SENT' || command === 'PRIV_QUEUED') {
      updatePendingMessage(parts[1], command === 'PRIV_SENT' ? 'Enviado' : 'Pendiente');
      showToast(command === 'PRIV_SENT' ? `Mensaje enviado a ${parts[1]}.` : `Se entregará cuando ${parts[1]} vuelva a conectarse.`);
      return;
    }
    if (command === 'PRIV_ERR') {
      markLatestPrivateAsFailed();
      showToast(`No se pudo enviar: ${(parts[1] || 'error').replace(/_/g, ' ')}.`, true);
      return;
    }
    if (command === 'MSG_ERR') {
      const message = parts.slice(1).join('|').replace(/_/g, ' ');
      if (state.resetRequired) setNotice(resetNotice, message, true);
      else showToast(message, true);
      return;
    }
    if (command === 'CONNECTION_CLOSED') {
      if (!state.authenticated) return;
      state.authenticated = false;
      chatApp.classList.add('hidden');
      authScreen.classList.remove('hidden');
      setNotice(authNotice, 'La conexión se cerró. Inicia sesión para volver a entrar.', true);
      sessionStorage.removeItem('echo-session');
      state.sessionId = null;
      void createSession().catch(() => {});
    }
  }

  function enterChat() {
    authScreen.classList.add('hidden');
    chatApp.classList.remove('hidden');
    chatApp.classList.remove('mobile-chat-open');
    byId('userName').textContent = state.username;
    byId('userAvatar').textContent = initials(state.username);
    setNotice(authNotice, '');
    void sendCommand('LIST_USERS').catch(error => showToast(error.message, true));
    selectConversation('room:General', 'room', 'General');
    if (!state.directoryTimer) state.directoryTimer = window.setInterval(() => {
      if (state.authenticated) void sendCommand('LIST_USERS').catch(() => {});
    }, 15000);
  }

  function selectConversation(key, type, name) {
    if (state.activeKey === key) {
      chatApp.classList.add('mobile-chat-open');
      return;
    }
    if (state.activeType === 'room' && state.activeName) {
      void sendCommand(`LEAVE_ROOM|${state.activeName}`).catch(() => {});
    }
    state.activeKey = key;
    state.activeType = type;
    state.activeName = name;
    chatApp.classList.add('mobile-chat-open');
    byId('emptyState').classList.add('hidden');
    byId('activeConversation').classList.remove('hidden');
    const channel = channels.find(item => item.name === name);
    byId('conversationName').textContent = name;
    byId('conversationAvatar').textContent = type === 'room' ? (channel ? channel.icon : '✳') : initials(name);
    byId('conversationAvatar').classList.toggle('person', type === 'person');
    byId('conversationPresence').innerHTML = type === 'room'
      ? `<i class="live-dot"></i>${escapeHtml(channel ? channel.note : 'Canal abierto')}`
      : `<i class="${state.people.find(person => person.name === name)?.online ? 'live-dot' : 'offline-mark'}"></i>${state.people.find(person => person.name === name)?.online ? 'En línea' : 'Cuenta de Echo'}`;
    byId('messageInput').maxLength = type === 'room' ? 190 : 4000;
    byId('messageInput').placeholder = type === 'room' ? 'Escribe al canal' : `Mensaje para ${name}`;
    renderDirectory();
    renderMessages();
    if (type === 'room') {
      void sendCommand(`JOIN_ROOM|${name}`).catch(error => showToast(error.message, true));
      void sendCommand(`GET_HIST|${name}|0`).catch(() => {});
    }
    byId('messageInput').focus({ preventScroll: true });
  }

  function refreshActivePresence() {
    if (state.activeType !== 'person') return;
    const person = state.people.find(item => item.name === state.activeName);
    const indicator = document.createElement('i');
    indicator.className = person?.online ? 'live-dot' : 'offline-mark';
    byId('conversationPresence').replaceChildren(indicator, document.createTextNode(person?.online ? 'En línea' : 'Cuenta de Echo'));
  }

  function renderDirectory() {
    const query = byId('directorySearch').value.trim().toLocaleLowerCase('es');
    conversationList.replaceChildren();
    const matchingChannels = channels.filter(channel => channel.name.toLocaleLowerCase('es').includes(query));
    const matchingPeople = state.people.filter(person => person.name.toLocaleLowerCase('es').includes(query));
    if (matchingChannels.length) {
      addListHeading(query ? 'CANALES' : 'CANALES ABIERTOS');
      for (const channel of matchingChannels) {
        addConversationItem({
          key: `room:${channel.name}`, name: channel.name, subtitle: channel.note,
          icon: channel.icon, type: 'room'
        });
      }
    }
    if (matchingPeople.length) {
      addListHeading(query ? 'PERSONAS' : 'PERSONAS');
      for (const person of matchingPeople) {
        addConversationItem({
          key: `person:${person.name}`, name: person.name,
          subtitle: person.online ? 'Disponible ahora' : 'Toca para enviar un mensaje',
          icon: initials(person.name), type: 'person', online: person.online
        });
      }
    }
    if (!matchingChannels.length && !matchingPeople.length) {
      const empty = document.createElement('p');
      empty.className = 'empty-list';
      empty.textContent = query ? 'No hay resultados. Prueba con otro nombre.' : 'Aún no hay más personas. Invita a alguien a crear su cuenta.';
      conversationList.append(empty);
    }
  }

  function addListHeading(text) {
    const heading = document.createElement('div');
    heading.className = 'list-section-label';
    heading.textContent = text;
    conversationList.append(heading);
  }

  function addConversationItem(item) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `conversation-item${state.activeKey === item.key ? ' active' : ''}`;
    button.setAttribute('aria-current', String(state.activeKey === item.key));
    const icon = document.createElement('span');
    icon.className = `list-avatar${item.type === 'person' ? ' person' : ''}${item.online ? ' online' : ''}`;
    icon.textContent = item.icon;
    const copy = document.createElement('span');
    copy.className = 'conversation-item-copy';
    const name = document.createElement('strong');
    name.textContent = item.name;
    const subtitle = document.createElement('small');
    subtitle.textContent = item.subtitle;
    copy.append(name, subtitle);
    const meta = document.createElement('span');
    meta.className = 'conversation-item-meta';
    if (item.type === 'person') {
      const status = document.createElement('i');
      status.className = item.online ? 'online-mark' : 'offline-mark';
      status.title = item.online ? 'En línea' : 'Desconectado';
      meta.append(status);
    } else {
      meta.textContent = 'CANAL';
    }
    button.append(icon, copy, meta);
    button.addEventListener('click', () => selectConversation(item.key, item.type, item.name));
    conversationList.append(button);
  }

  function appendMessage(key, message) {
    const messages = state.messages.get(key) || [];
    if (message.id && messages.some(existing => existing.id === message.id)) return;
    if (!message.id && messages.some(existing => !existing.id && existing.author === message.author
        && existing.time === message.time && existing.text === message.text)) return;
    messages.push(message);
    messages.sort((a, b) => (a.time || '').localeCompare(b.time || ''));
    state.messages.set(key, messages);
    if (state.activeKey === key) renderMessages();
  }

  function renderMessages() {
    if (!state.activeKey) return;
    const messages = state.messages.get(state.activeKey) || [];
    messageList.replaceChildren();
    if (!messages.length) {
      const empty = document.createElement('div');
      empty.className = 'system-message';
      empty.textContent = state.activeType === 'room'
        ? `Este es el comienzo del canal ${state.activeName}. Saluda a todos.`
        : `Esta es tu conversación con ${state.activeName}. Envía el primer mensaje.`;
      messageList.append(empty);
      return;
    }
    let previousDay = '';
    for (const message of messages) {
      const day = (message.time || '').slice(0, 10);
      if (day && day !== previousDay) {
        const divider = document.createElement('div');
        divider.className = 'date-divider';
        divider.textContent = formatDay(day);
        messageList.append(divider);
        previousDay = day;
      }
      messageList.append(createMessageElement(message));
    }
    messageList.scrollTop = messageList.scrollHeight;
  }

  function createMessageElement(message) {
    const row = document.createElement('article');
    row.className = `message-row${message.mine ? ' mine' : ''}`;
    if (!message.mine) {
      const avatar = document.createElement('span');
      avatar.className = 'message-mini-avatar';
      avatar.textContent = initials(message.author || state.activeName);
      row.append(avatar);
    }
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    if (state.activeType === 'room' && !message.mine) {
      const author = document.createElement('span');
      author.className = 'message-author';
      author.textContent = message.author || 'Echo';
      bubble.append(author);
    }
    const text = document.createElement('div');
    text.className = 'message-text';
    text.textContent = message.text;
    const meta = document.createElement('div');
    meta.className = 'message-meta';
    const time = document.createElement('time');
    time.textContent = formatTime(message.time);
    meta.append(time);
    if (message.mine && state.activeType === 'person') {
      const status = document.createElement('span');
      status.className = 'message-check';
      status.textContent = message.delivery === 'Pendiente' ? '◷' : message.delivery === 'No enviado' ? '!' : '✓';
      if (message.delivery === 'No enviado') status.classList.add('failed');
      status.title = message.delivery || 'Enviado';
      meta.append(status);
    }
    bubble.append(text, meta);
    row.append(bubble);
    return row;
  }

  async function submitMessage(event) {
    event.preventDefault();
    const field = byId('messageInput');
    const text = field.value.trim().replace(/[\r\n]+/g, ' ');
    if (!text || !state.activeKey || !state.authenticated) return;
    field.value = '';
    resizeComposer();
    byId('characterCount').textContent = '';
    try {
      if (state.activeType === 'room') {
        await sendCommand(`MSG_ROOM|${state.activeName}|${text}`);
      } else {
        const time = localTimestamp();
        const clientId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        appendMessage(state.activeKey, { author: state.username, time, text, mine: true, delivery: 'Enviando', clientId });
        const pending = state.pendingPrivate.get(state.activeName) || [];
        pending.push(clientId);
        state.pendingPrivate.set(state.activeName, pending);
        await sendCommand(`MSG_PRIV|${state.activeName}|${text}`);
      }
    } catch (error) {
      showToast(error.message, true);
      field.value = text;
    }
  }

  function updatePendingMessage(recipient, status) {
    const pending = state.pendingPrivate.get(recipient) || [];
    const clientId = pending.shift();
    state.pendingPrivate.set(recipient, pending);
    if (!clientId) return;
    const messages = state.messages.get(`person:${recipient}`) || [];
    const message = messages.find(item => item.clientId === clientId);
    if (message) {
      message.delivery = status;
      if (state.activeKey === `person:${recipient}`) renderMessages();
    }
  }

  function markLatestPrivateAsFailed() {
    let latest = null;
    for (const messages of state.messages.values()) {
      for (const message of messages) {
        if (message.clientId && message.delivery === 'Enviando'
            && (!latest || message.clientId > latest.clientId)) latest = message;
      }
    }
    if (latest) {
      latest.delivery = 'No enviado';
      for (const [key, messages] of state.messages.entries()) {
        if (messages.includes(latest) && key === state.activeKey) renderMessages();
      }
    }
  }

  function localTimestamp() {
    const now = new Date();
    const two = number => String(number).padStart(2, '0');
    return `${now.getFullYear()}-${two(now.getMonth() + 1)}-${two(now.getDate())} ${two(now.getHours())}:${two(now.getMinutes())}`;
  }

  function formatTime(value = '') {
    const match = value.match(/\d{4}-\d{2}-\d{2} (\d{2}:\d{2})/);
    if (match) return match[1];
    return value;
  }

  function formatDay(value) {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    if (date.toDateString() === today.toDateString()) return 'HOY';
    return new Intl.DateTimeFormat('es', { day: 'numeric', month: 'long' }).format(date).toUpperCase();
  }

  function initials(value = '') {
    const clean = value.trim();
    return clean ? Array.from(clean)[0].toLocaleUpperCase('es') : 'E';
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }

  function showToast(message, isError = false) {
    const toast = byId('toast');
    toast.textContent = message;
    toast.classList.toggle('error', isError);
    toast.classList.add('visible');
    window.clearTimeout(state.toastTimer);
    state.toastTimer = window.setTimeout(() => toast.classList.remove('visible'), 3000);
  }

  function resizeComposer() {
    const input = byId('messageInput');
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
  }

  async function logout() {
    const sessionId = state.sessionId;
    state.authenticated = false;
    state.resetRequired = false;
    state.activeKey = null;
    state.activeType = null;
    state.activeName = null;
    state.messages.clear();
    chatApp.classList.add('hidden');
    chatApp.classList.remove('mobile-chat-open');
    authScreen.classList.remove('hidden');
    byId('resetForm').classList.add('hidden');
    authForm.classList.remove('hidden');
    byId('authTabs').classList.remove('hidden');
    byId('authTitle').classList.remove('hidden');
    byId('authSubtitle').classList.remove('hidden');
    byId('authNotice').classList.remove('hidden');
    byId('authFootnote').classList.remove('hidden');
    byId('authForm').reset();
    setMode('login');
    if (sessionId) {
      try {
        await fetch(`/api/session?id=${encodeURIComponent(sessionId)}`, { method: 'DELETE', keepalive: true });
      } catch (_) { /* El socket se cerrará al expirar si el servidor ya no responde. */ }
      sessionStorage.removeItem('echo-session');
      state.sessionId = null;
      try { await createSession(); }
      catch (error) { setNotice(authNotice, error.message, true); }
    }
  }

  function wireEvents() {
    byId('loginTab').addEventListener('click', () => setMode('login'));
    byId('registerTab').addEventListener('click', () => setMode('register'));
    authForm.addEventListener('submit', onAuthSubmit);
    byId('resetForm').addEventListener('submit', onResetSubmit);
    byId('togglePassword').addEventListener('click', () => {
      const input = byId('password');
      input.type = input.type === 'password' ? 'text' : 'password';
      byId('togglePassword').setAttribute('aria-label', input.type === 'password' ? 'Mostrar contraseña' : 'Ocultar contraseña');
    });
    byId('directorySearch').addEventListener('input', renderDirectory);
    byId('refreshDirectory').addEventListener('click', () => void sendCommand('LIST_USERS').catch(error => showToast(error.message, true)));
    byId('newChatButton').addEventListener('click', () => byId('directorySearch').focus());
    byId('findPeopleButton').addEventListener('click', () => {
      byId('directorySearch').focus();
      showToast('Busca una persona o abre uno de los canales.');
    });
    byId('backToList').addEventListener('click', () => chatApp.classList.remove('mobile-chat-open'));
    byId('logoutButton').addEventListener('click', logout);
    byId('composerForm').addEventListener('submit', submitMessage);
    byId('messageInput').addEventListener('input', () => {
      resizeComposer();
      const length = byId('messageInput').value.length;
      const limit = byId('messageInput').maxLength;
      byId('characterCount').textContent = length > limit - 25 ? `${length}/${limit}` : '';
    });
    byId('messageInput').addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        byId('composerForm').requestSubmit();
      }
    });
    byId('emojiButton').addEventListener('click', () => {
      const input = byId('messageInput');
      const start = input.selectionStart;
      const end = input.selectionEnd;
      input.value = `${input.value.slice(0, start)}🙂${input.value.slice(end)}`;
      input.focus();
      input.setSelectionRange(start + 2, start + 2);
      resizeComposer();
    });
    byId('conversationSearchButton').addEventListener('click', () => {
      byId('directorySearch').focus();
      showToast('Usa el buscador lateral para encontrar una conversación.');
    });
    document.addEventListener('keydown', event => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        byId('directorySearch').focus();
      }
      if (event.key === 'Escape' && chatApp.classList.contains('mobile-chat-open')) {
        chatApp.classList.remove('mobile-chat-open');
      }
    });
  }

  async function start() {
    wireEvents();
    try {
      await ensureSession();
      void pollEvents();
      window.setInterval(pollEvents, 850);
      window.setInterval(() => {
        if (state.authenticated) void sendCommand('PING').catch(() => {});
      }, 60000);
    } catch (error) {
      setNotice(authNotice, error.message || 'Inicia el servidor Echo y vuelve a cargar esta página.', true);
    }
  }

  start();
})();

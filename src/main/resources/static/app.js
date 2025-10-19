// Configuration
const API_BASE_URL = 'https://localhost:8080/api/chat';

// DOM Elements
const newChatBtn = document.getElementById('new-chat-btn');
const toggleSidebarBtn = document.getElementById('toggle-sidebar-btn');
const sidebar = document.getElementById('sidebar');
const conversationList = document.getElementById('conversation-list');
const messagesDiv = document.getElementById('messages');
const chatTitle = document.getElementById('chat-title');
const inputForm = document.getElementById('input-form');
const promptInput = document.getElementById('prompt');
const sendBtn = document.getElementById('send-btn');
const sendBtnText = document.getElementById('send-btn-text');

// State
let currentChatId = null;
let isLoading = false;
let conversations = [];

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadAllConversations();
    setupEventListeners();
    configureMarked();
});

// Configure Marked.js options
function configureMarked() {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

// Event Listeners
function setupEventListeners() {
    newChatBtn.addEventListener('click', createNewChat);
    toggleSidebarBtn.addEventListener('click', toggleSidebar);
    inputForm.addEventListener('submit', handleSendMessage);
    promptInput.addEventListener('input', autoResizeTextarea);
    promptInput.addEventListener('keydown', handleEnterKey);
}

// Toggle sidebar collapse/expand
function toggleSidebar() {
    sidebar.classList.toggle('collapsed');
}

// Auto-resize textarea
function autoResizeTextarea() {
    promptInput.style.height = 'auto';
    promptInput.style.height = promptInput.scrollHeight + 'px';
}

// Handle Enter key (send on Enter, new line on Shift+Enter)
function handleEnterKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        inputForm.dispatchEvent(new Event('submit'));
    }
}

// Load all conversations from database
async function loadAllConversations() {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations`);

        if (!response.ok) {
            throw new Error('Falha ao carregar conversas');
        }

        conversations = await response.json();
        renderConversationList();

        // Se não houver conversas, cria uma automaticamente
        if (conversations.length === 0) {
            await createNewChat();
        }
    } catch (error) {
        console.error('Erro ao carregar conversas:', error);
        conversationList.innerHTML = '<div class="empty-conversations">Erro ao carregar conversas</div>';
    }
}

// Render conversation list in sidebar
function renderConversationList() {
    if (conversations.length === 0) {
        conversationList.innerHTML = '<div class="empty-conversations">Nenhuma conversa ainda</div>';
        return;
    }

    conversationList.innerHTML = '';

    conversations.forEach(conv => {
        const item = document.createElement('div');
        item.className = 'conversation-item';
        if (conv.conversationId === currentChatId) {
            item.classList.add('active');
        }

        // Content wrapper (title + delete button)
        const contentDiv = document.createElement('div');
        contentDiv.className = 'conversation-content';

        const titleSpan = document.createElement('span');
        titleSpan.className = 'conversation-title';
        titleSpan.textContent = conv.title;
        titleSpan.title = conv.title; // Tooltip com título completo

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.innerHTML = '🗑️';
        deleteBtn.title = 'Deletar conversa';
        deleteBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            deleteConversation(conv.conversationId);
        });

        contentDiv.appendChild(titleSpan);
        contentDiv.appendChild(deleteBtn);

        // Date display
        const dateSpan = document.createElement('div');
        dateSpan.className = 'conversation-date';
        dateSpan.textContent = formatDate(conv.lastMessageTime);

        item.appendChild(contentDiv);
        item.appendChild(dateSpan);

        item.addEventListener('click', () => loadConversation(conv.conversationId));

        conversationList.appendChild(item);
    });
}

// Format date for display
function formatDate(timestamp) {
    if (!timestamp) return '';

    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) {
        return 'Agora';
    } else if (diffMins < 60) {
        return `${diffMins} min atrás`;
    } else if (diffHours < 24) {
        return `${diffHours}h atrás`;
    } else if (diffDays < 7) {
        return `${diffDays}d atrás`;
    } else {
        return date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
    }
}

// Create new conversation
async function createNewChat() {
    if (isLoading) return;

    try {
        setLoading(true);
        newChatBtn.disabled = true;

        const response = await fetch(`${API_BASE_URL}/generate-chat-id`);

        if (!response.ok) {
            throw new Error('Falha ao criar nova conversa');
        }

        currentChatId = await response.text();

        // Limpar mensagens
        messagesDiv.innerHTML = '<div class="empty-chat"><p>👋 Olá! Como posso ajudar você hoje?</p></div>';
        chatTitle.textContent = 'Nova Conversa';

        // Habilitar input
        sendBtn.disabled = false;
        promptInput.disabled = false;
        promptInput.focus();

        // Recarregar lista (a conversa aparecerá após primeira mensagem)
        await loadAllConversations();

    } catch (error) {
        console.error('Erro ao criar conversa:', error);
        alert('Erro ao criar nova conversa. Tente novamente.');
    } finally {
        setLoading(false);
        newChatBtn.disabled = false;
    }
}

// Load specific conversation messages
async function loadConversation(chatId) {
    if (isLoading || chatId === currentChatId) return;

    try {
        setLoading(true);
        currentChatId = chatId;

        const response = await fetch(`${API_BASE_URL}/${chatId}/messages`);

        if (!response.ok) {
            throw new Error('Falha ao carregar mensagens');
        }

        const messages = await response.json();

        // Atualizar UI
        const conv = conversations.find(c => c.conversationId === chatId);
        chatTitle.textContent = conv ? conv.title : 'Conversa';

        // Renderizar mensagens
        messagesDiv.innerHTML = '';

        if (messages.length === 0) {
            messagesDiv.innerHTML = '<div class="empty-chat"><p>Nenhuma mensagem ainda. Comece a conversar!</p></div>';
        } else {
            messages.forEach(msg => {
                addMessageToUI(msg.role, msg.text, false);
            });
            scrollToBottom();
        }

        // Atualizar sidebar
        renderConversationList();

        // Habilitar input
        sendBtn.disabled = false;
        promptInput.disabled = false;
        promptInput.focus();

    } catch (error) {
        console.error('Erro ao carregar conversa:', error);
        alert('Erro ao carregar conversa.');
    } finally {
        setLoading(false);
    }
}

// Delete conversation
async function deleteConversation(chatId) {
    if (!confirm('Tem certeza que deseja deletar esta conversa?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/${chatId}/messages`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('Falha ao deletar conversa');
        }

        // Se deletou a conversa atual, limpar tela
        if (chatId === currentChatId) {
            currentChatId = null;
            messagesDiv.innerHTML = '<div class="empty-chat"><p>Conversa deletada. Crie uma nova!</p></div>';
            chatTitle.textContent = 'Selecione ou crie uma conversa';
            sendBtn.disabled = true;
            promptInput.disabled = true;
        }

        // Recarregar lista
        await loadAllConversations();

    } catch (error) {
        console.error('Erro ao deletar conversa:', error);
        alert('Erro ao deletar conversa.');
    }
}

// Handle send message
async function handleSendMessage(e) {
    e.preventDefault();

    const question = promptInput.value.trim();

    if (!question || !currentChatId || isLoading) {
        return;
    }

    try {
        setLoading(true);

        // Limpar input
        promptInput.value = '';
        promptInput.style.height = 'auto';

        // Remover mensagem vazia se existir
        const emptyChat = messagesDiv.querySelector('.empty-chat');
        if (emptyChat) {
            emptyChat.remove();
        }

        // Adicionar mensagem do usuário
        addMessageToUI('user', question, true);

        // Criar elemento para resposta da IA
        const aiMessageDiv = document.createElement('div');
        aiMessageDiv.className = 'message ai-message';
        aiMessageDiv.innerHTML = '<div class="loading-spinner"></div>';
        messagesDiv.appendChild(aiMessageDiv);
        scrollToBottom();

        // Stream da resposta
        await streamAIResponse(question, aiMessageDiv);

        // Recarregar conversas para atualizar título
        await loadAllConversations();

    } catch (error) {
        console.error('Erro ao enviar mensagem:', error);
        alert('Erro ao enviar mensagem. Tente novamente.');
    } finally {
        setLoading(false);
    }
}

// Stream AI response using Server-Sent Events
async function streamAIResponse(question, aiMessageDiv) {
    return new Promise((resolve, reject) => {
        const eventSource = new EventSource(
            `${API_BASE_URL}/stream?chatId=${encodeURIComponent(currentChatId)}&question=${encodeURIComponent(question)}`
        );

        let fullResponse = '';

        eventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                fullResponse += data.value;

                // Renderizar Markdown e sanitizar
                const htmlContent = marked.parse(fullResponse);
                const sanitizedContent = DOMPurify.sanitize(htmlContent);

                aiMessageDiv.innerHTML = sanitizedContent;
                scrollToBottom();
            } catch (error) {
                console.error('Erro ao processar chunk:', error);
            }
        };

        eventSource.onerror = (error) => {
            console.log('Stream finalizado ou erro:', error);
            eventSource.close();

            // Se não recebeu nenhuma resposta, mostrar erro
            if (!fullResponse) {
                aiMessageDiv.innerHTML = '<em>Erro ao receber resposta. Tente novamente.</em>';
                reject(error);
            } else {
                resolve();
            }
        };
    });
}

// Add message to UI
function addMessageToUI(role, content, shouldScroll = true) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;

    if (role === 'user' || role === 'USER') {
        messageDiv.textContent = content;
    } else {
        // Para mensagens da IA, renderizar Markdown
        const htmlContent = marked.parse(content);
        const sanitizedContent = DOMPurify.sanitize(htmlContent);
        messageDiv.innerHTML = sanitizedContent;
    }

    messagesDiv.appendChild(messageDiv);

    if (shouldScroll) {
        scrollToBottom();
    }
}

// Scroll to bottom of messages
function scrollToBottom() {
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

// Set loading state
function setLoading(loading) {
    isLoading = loading;
    promptInput.disabled = loading;
    sendBtn.disabled = loading || !currentChatId;

    if (loading) {
        sendBtnText.innerHTML = '<span class="loading-spinner"></span>';
    } else {
        sendBtnText.textContent = 'Enviar';
    }
}

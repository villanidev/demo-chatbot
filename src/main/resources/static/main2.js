// DOM Elements
const loginScreen = document.getElementById('login-screen');
const loginBox = document.getElementById('login-box');
const usernameInput = document.getElementById('username-input');
const loginBtn = document.getElementById('login-btn');

const sidebarDiv = document.getElementById('sidebar');
const conversationListDiv = document.getElementById('conversation-list');
const messagesContainerDiv = document.getElementById('messages');
const promptInput = document.getElementById('prompt');
const sendBtn = document.getElementById('send-btn');
const newChatBtn = document.getElementById('new-chat-btn');

// State
let currentUser = null;
let currentChatId = null;
let currentMessages = [];
let conversations = [];

// on load start new chat ID
document.addEventListener("DOMContentLoaded", async () => {
    try {
        const response = await fetch(`https://localhost:8080/api/chat/generate-chat-id`, {
            method: 'GET'
        });

        currentChatId = await response.text();
        console.log(currentChatId);
        currentMessages = [];
        messagesContainerDiv.innerHTML = '';
        loadConversations();

    } catch (error) {
        console.error('Failed to create conversation:', error);
    }
});

// New conversation handler
newChatBtn.addEventListener('click', async () => {
    try {
        const response = await fetch(`https://localhost:8080/api/chat/generate-chat-id`, {
            method: 'GET'
        });

        currentChatId = await response.text();
        console.log(currentChatId);
        currentMessages = [];
        messagesContainerDiv.innerHTML = '';
        loadConversations();

    } catch (error) {
        console.error('Failed to create conversation:', error);
    }
});

// generate chat id
async function generateChatId() {
    try {
        const response = await fetch(`https://localhost:8080/api/chat/generate-chat-id`, {
            method: 'GET'
        });

        currentChatId = await response.text();
        console.log(currentChatId);
        currentMessages = [];
        messagesContainerDiv.innerHTML = '';
        loadConversations();

    } catch (error) {
        console.error('Failed to create conversation:', error);
    }
}

// Load conversations list
async function loadConversations() {
    try {
        const response = await fetch(`https://localhost:8080/api/chat/${currentChatId}/messages`, {
            method: 'GET'
        });

        conversations = await response.json();

        if (conversations) {
            conversations.forEach(msg => {
                console.log(msg)
            });
        }

        renderConversationList();
    } catch (error) {
        console.error('Failed to load conversations:', error);
    }
}

// Render conversation list
function renderConversationList() {
    conversationListDiv.innerHTML = '';
    conversations.forEach(conversation => {
        const item = document.createElement('div');
        item.className = 'conversation-item';
        if (conversation.id === currentChatId) {
            item.classList.add('active');
        }

        const titleSpan = document.createElement('span');
        titleSpan.textContent = currentChatId;

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.innerHTML = '✕';

        deleteBtn.addEventListener('click', async (e) => {
            e.stopPropagation();
            if (confirm('Delete this conversation?')) {
                await deleteConversation(conversation.id);
            }
        });

        item.appendChild(titleSpan);
        item.appendChild(deleteBtn);
        item.addEventListener('click', () => loadConversation(conversation.id));

        conversationListDiv.appendChild(item);
    });
}

// Delete conversation
async function deleteConversation(conversationId) {
    try {
        await fetch(`https://localhost:8080/api/chat/${currentChatId}/messages`, {
            method: 'DELETE'
        });

        if (currentChatId === conversationId) {
            currentChatId = null;
            currentMessages = [];
            messagesContainerDiv.innerHTML = '';
        }

        await loadConversations();
    } catch (error) {
        console.error('Failed to delete conversation:', error);
    }
}

// Load conversation messages (implement pagination in future)
async function loadConversation(conversationId) {
    currentChatId = conversationId;
    currentMessages = [];

    try {
        const response = await fetch(`https://localhost:8080/api/chat/${currentChatId}/messages`, {
            method: 'GET'
        });

        const messages = await response.json();
        currentMessages = messages;

        // Clear and render messages
        messagesContainerDiv.innerHTML = '';
        messages.forEach(message => {
            addMessage(message.role, message.text, false);
        });

        // Update active conversation in list
        renderConversationList();
    } catch (error) {
        console.error('Failed to load conversation:', error);
    }
}

// Add message to UI
function addMessage(role, content, scroll = true) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    messageDiv.innerHTML = content;
    messagesContainerDiv.appendChild(messageDiv);

    if (scroll) {
        messagesContainerDiv.scrollTop = messagesContainerDiv.scrollHeight;
    }
}

// Send message handler
sendBtn.addEventListener('click', sendMessage);
promptInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        sendMessage();
    }
});

async function sendMessage() {
    const prompt = promptInput.value.trim();
    if (!prompt || !currentChatId) return;

    promptInput.value = '';
    promptInput.disabled = true;
    sendBtn.disabled = true;

    addMessage('user', prompt);

    try {
        const eventSource = new EventSource(
            `https://localhost:8080/api/chat/stream?chatId=${currentChatId}&question=${encodeURIComponent(prompt)}`
        );

        const aiMessageDiv = document.createElement('div');
        aiMessageDiv.className = 'message ai-message';
        messagesContainerDiv.appendChild(aiMessageDiv);

        // render llm stream response and sanitize and parse the final markdown
        // https://developer.chrome.com/docs/ai/render-llm-responses#dom_sanitizer_and_streaming_markdown_parser
        let chunkResponses = '';
        eventSource.onmessage = (e) => {
            chunkResponses += JSON.parse(e.data).value;

            // Sanitize all chunks received so far.
            DOMPurify.sanitize(chunkResponses);

            // Check if the output was insecure.
            if (DOMPurify.removed.length) {
               // If the output was insecure, immediately stop what you were doing.
               // Reset the parser and flush the remaining Markdown.
               chunkResponses = '';
               return;
            }

            aiMessageDiv.innerHTML = marked.parse(chunkResponses);;
            messagesContainerDiv.scrollTop = messagesContainerDiv.scrollHeight;
        };

        eventSource.onerror = () => {
            eventSource.close();
            promptInput.disabled = false;
            sendBtn.disabled = false;
            currentMessages.push({ role: 'ai', content: chunkResponses });
            loadConversations();
        };
    } catch (error) {
        console.error('Failed to send message:', error);
        addMessage('ai', 'Sorry, an error occurred. Please try again.');
    }
}
const form = document.getElementById('input-form');
const promptInput = document.getElementById('prompt');
const messagesDiv = document.getElementById('messages');
const historyList = document.getElementById('history-list');
const sendBtn = document.getElementById('send-btn');
const chatLoaderDiv = document.getElementById('loader');
let currentChatId = null;

// Start new conversation
document.addEventListener("DOMContentLoaded", async () => {
    try {
        const response = await fetch('https://localhost:8080/api/chat/generate-chat-id', {
              method: 'GET'
        });

        currentChatId = await response.text();
        console.log(currentChatId);
        loadConversation(currentChatId);
      } catch (error) {
        console.error(error.message);
      }
});

// Load conversation history
async function loadConversation(currentChatId) {
  try {
    const response = await fetch(`https://localhost:8080/api/chat/${currentChatId}/messages`, {
          method: 'GET'
    });

    if (!response.ok) {
        throw new Error(`Response status: ${response.status}`);
    }

    const messages = await response.json();

    if (messages) {
        messages.forEach(msg => {
            console.log(msg.role, msg.text)
            addMessage(msg.role, msg.text);
        });
    }

  } catch (error) {
    console.error(error.message);
  }
}

// Store chat history
let chatHistory = [];

// Handle form submission
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const question = promptInput.value;
  promptInput.value = '';
  promptInput.disabled = true;
  sendBtn.disabled = true;

  // Add user message to UI and history
  addMessage('user', question);

  // Stream AI response
  const eventSource = new EventSource(
    `https://localhost:8080/api/chat/stream?chatId=${currentChatId}&question=${encodeURIComponent(question)}`
  );

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

    const lastAiMessage = messagesDiv.querySelector('.ai-message:last-child') || addMessage('ai', '');
    if (lastAiMessage) {
        lastAiMessage.innerHTML = marked.parse(chunkResponses);
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
  };

  eventSource.onerror = (e) => {
    eventSource.close();
    chatHistory.push({ role: 'ai', content: chunkResponses });
    promptInput.disabled = false;
    sendBtn.disabled = false;
    loadConversation(currentChatId);
  }
});

// Add a message to the UI
function addMessage(role, content) {
  const messageDiv = document.createElement('div');
  messageDiv.className = `message ${role}-message`;
  if (role === 'user' || role === 'USER') {
    messageDiv.innerHTML = `You: ${content}`;
  } else {
    messageDiv.innerHTML = `Bot: ${content}`;
  }
  messagesDiv.appendChild(messageDiv);
  messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function toggleLoading() {
    if (chatLoaderDiv.style.display === "none") {
        chatLoaderDiv.style.display = "block";
    } else {
        chatLoaderDiv.style.display = "none";
    }
}
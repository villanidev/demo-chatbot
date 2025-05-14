const form = document.getElementById('input-form');
const promptInput = document.getElementById('prompt');
const messagesDiv = document.getElementById('messages');
const historyList = document.getElementById('history-list');

// Store chat history
let chatHistory = [];

// Handle form submission
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const question = promptInput.value;
  promptInput.value = '';

  // Add user message to UI and history
  addMessage('user', question);
  chatHistory.push({ role: 'user', content: question });

  // Stream AI response
  const eventSource = new EventSource(
    `http://localhost:8080/api/chat/stream?chatId=1&question=${encodeURIComponent(question)}`
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
    //messagesDiv.querySelector('.ai-message:last-child').innerHTML = marked.parse(messagesDiv.querySelector('.ai-message:last-child').textContent)
    chatHistory.push({ role: 'ai', content: chunkResponses });
    updateHistorySidebar();
  }
});

// Update sidebar with chat history
function updateHistorySidebar() {
  historyList.innerHTML = '';
  chatHistory.forEach((msg, index) => {
    if (msg.role === 'user') {
      const li = document.createElement('li');
      li.textContent = `You: ${msg.content.slice(0, 30)}...`;
      li.onclick = () => loadHistory(index);
      historyList.appendChild(li);
    }
  });
}

// Add a message to the UI
function addMessage(role, content) {
  const messageDiv = document.createElement('div');
  messageDiv.className = `message ${role}-message`;
  if (role === 'user') {
    messageDiv.innerHTML = `You: ${content}`;
  } else {
    messageDiv.innerHTML = `Bot: ${content}`;
  }
  messagesDiv.appendChild(messageDiv);
  messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

// Load a past conversation (simplified)
function loadHistory(index) {
  messagesDiv.innerHTML = '';
  // Logic to reconstruct the conversation
}

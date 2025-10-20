// ====================== RAG SYSTEM JAVASCRIPT ====================== //

// Global state
let documents = [];
let isProcessing = false;

// DOM elements
const uploadArea = document.getElementById("uploadArea");
const fileInput = document.getElementById("fileInput");
const documentsList = document.getElementById("documentsList");
const documentsCount = document.getElementById("documentsCount");
const chatMessages = document.getElementById("chatMessages");
const chatInput = document.getElementById("chatInput");
const sendBtn = document.getElementById("sendBtn");
const responseTime = document.getElementById("responseTime");

// Sidebar elements
const leftSidebar = document.getElementById("leftSidebar");
const rightSidebar = document.getElementById("rightSidebar");
const toggleLeftBtn = document.getElementById("toggle-left-sidebar-btn");
const toggleRightBtn = document.getElementById("toggle-right-sidebar-btn");

// Initialize
document.addEventListener("DOMContentLoaded", function () {
  setupEventListeners();
  loadDocuments();
  chatInput.focus();
});

// Event Listeners Setup
function setupEventListeners() {
  // Upload area events
  uploadArea.addEventListener("click", () => fileInput.click());
  uploadArea.addEventListener("dragover", handleDragOver);
  uploadArea.addEventListener("dragleave", handleDragLeave);
  uploadArea.addEventListener("drop", handleDrop);

  // File input
  fileInput.addEventListener("change", handleFileSelect);

  // Chat input events
  chatInput.addEventListener("input", handleInputChange);
  chatInput.addEventListener("keydown", handleKeyDown);
  sendBtn.addEventListener("click", sendMessage);

  // Auto-resize textarea
  chatInput.addEventListener("input", autoResizeTextarea);

  // Sidebar toggle events
  toggleLeftBtn.addEventListener("click", toggleLeftSidebar);
  toggleRightBtn.addEventListener("click", toggleRightSidebar);
}

// Sidebar toggle functions
function toggleLeftSidebar() {
  leftSidebar.classList.toggle("collapsed");
}

function toggleRightSidebar() {
  rightSidebar.classList.toggle("collapsed");
}

// Drag and Drop handlers
function handleDragOver(e) {
  e.preventDefault();
  uploadArea.classList.add("dragover");
}

function handleDragLeave(e) {
  e.preventDefault();
  uploadArea.classList.remove("dragover");
}

function handleDrop(e) {
  e.preventDefault();
  uploadArea.classList.remove("dragover");
  const files = Array.from(e.dataTransfer.files);
  uploadFiles(files);
}

// File selection handler
function handleFileSelect(e) {
  const files = Array.from(e.target.files);
  uploadFiles(files);
}

// Upload files
async function uploadFiles(files) {
  for (const file of files) {
    await uploadSingleFile(file);
  }
}

// Upload single file
async function uploadSingleFile(file) {
  const formData = new FormData();
  formData.append("file", file);

  // Add document to UI immediately
  const tempDoc = {
    id: Date.now(),
    filename: file.name,
    status: "PROCESSING",
    uploadedAt: new Date(),
    fileSize: file.size,
    contentType: file.type,
  };

  documents.unshift(tempDoc);
  updateDocumentsList();

  try {
    const response = await fetch("/api/rag/documents/upload", {
      method: "POST",
      body: formData,
    });

    const result = await response.json();

    if (response.ok) {
      // Update document with real data
      const docIndex = documents.findIndex((d) => d.id === tempDoc.id);
      if (docIndex !== -1) {
        documents[docIndex] = {
          ...result,
          uploadedAt: new Date(),
        };
      }
    } else {
      // Mark as error
      const docIndex = documents.findIndex((d) => d.id === tempDoc.id);
      if (docIndex !== -1) {
        documents[docIndex].status = "ERROR";
        documents[docIndex].errorMessage = result.message || "Upload failed";
      }
    }
  } catch (error) {
    console.error("Upload error:", error);
    const docIndex = documents.findIndex((d) => d.id === tempDoc.id);
    if (docIndex !== -1) {
      documents[docIndex].status = "ERROR";
      documents[docIndex].errorMessage = "Network error";
    }
  }

  updateDocumentsList();
  fileInput.value = "";
}

// Load documents from server
async function loadDocuments() {
  try {
    const response = await fetch("/api/rag/documents");
    if (response.ok) {
      documents = await response.json();
      updateDocumentsList();
    }
  } catch (error) {
    console.error("Failed to load documents:", error);
  }
}

// Update documents list UI
function updateDocumentsList() {
  documentsCount.textContent = documents.length;

  if (documents.length === 0) {
    documentsList.innerHTML = `
      <div class="empty-state">
        <div class="empty-title">Nenhum documento</div>
        <div class="empty-text">Faça upload de documentos para começar</div>
      </div>
    `;
    return;
  }

  documentsList.innerHTML = documents
    .map(
      (doc) => `
      <div class="document-item" data-id="${doc.id}">
        <div class="document-actions">
          <button class="action-btn" onclick="deleteDocument(${
            doc.id
          })" title="Deletar">
            🗑️
          </button>
        </div>
        <div class="document-name">${doc.filename}</div>
        <div class="document-meta">
          <span class="document-status status-${doc.status.toLowerCase()}">
            ${doc.status}
          </span>
          <span>${formatFileSize(doc.fileSize || 0)}</span>
        </div>
        ${
          doc.chunkCount
            ? `<div style="font-size: 11px; color: var(--text-light); margin-top: 4px;">
            ${doc.chunkCount} chunks processados
        </div>`
            : ""
        }
        ${
          doc.status === "PROCESSING"
            ? '<div class="progress-bar"><div class="progress-fill"></div></div>'
            : ""
        }
      </div>
    `
    )
    .join("");
}

// Delete document
async function deleteDocument(docId) {
  if (!confirm("Tem certeza que deseja deletar este documento?")) {
    return;
  }

  try {
    const response = await fetch(`/api/rag/documents/${docId}`, {
      method: "DELETE",
    });

    if (response.ok) {
      documents = documents.filter((d) => d.id !== docId);
      updateDocumentsList();
    } else {
      alert("Erro ao deletar documento");
    }
  } catch (error) {
    console.error("Delete error:", error);
    alert("Erro de conexão ao deletar documento");
  }
}

// Chat input handlers
function handleInputChange() {
  const hasText = chatInput.value.trim().length > 0;
  const hasDocuments = documents.some((d) => d.status === "COMPLETED");
  sendBtn.disabled = !hasText || !hasDocuments || isProcessing;
}

function handleKeyDown(e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    if (!sendBtn.disabled) {
      sendMessage();
    }
  }
}

function autoResizeTextarea() {
  chatInput.style.height = "auto";
  chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + "px";
}

// Send message
async function sendMessage() {
  const message = chatInput.value.trim();
  if (!message || isProcessing) return;

  // Add user message to chat
  addMessageToChat("user", message);

  // Clear input
  chatInput.value = "";
  chatInput.style.height = "auto";
  isProcessing = true;
  handleInputChange();

  // Show typing indicator
  const typingId = addMessageToChat("assistant", "Pensando...", [], true);

  const startTime = Date.now();

  try {
    const response = await fetch("/api/rag/query", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        question: message,
        topK: 5,
      }),
    });

    const result = await response.json();
    const endTime = Date.now();
    responseTime.textContent = `${endTime - startTime}ms`;

    // Remove typing indicator
    removeMessage(typingId);

    if (response.ok) {
      addMessageToChat("assistant", result.answer, result.citations || []);
    } else {
      addMessageToChat(
        "assistant",
        "Desculpe, ocorreu um erro ao processar sua pergunta.",
        []
      );
    }
  } catch (error) {
    console.error("Query error:", error);
    removeMessage(typingId);
    addMessageToChat("assistant", "Erro de conexão. Tente novamente.", []);
  } finally {
    isProcessing = false;
    handleInputChange();
    chatInput.focus();
  }
}

// Add message to chat
function addMessageToChat(sender, text, citations = [], isTemporary = false) {
  const messageId =
    "msg-" + Date.now() + "-" + Math.random().toString(36).substr(2, 9);
  const isUser = sender === "user";

  const messageHtml = `
    <div class="message ${sender}" id="${messageId}">
      <div class="message-avatar">
        ${isUser ? "U" : "🤖"}
      </div>
      <div class="message-content">
        <div class="message-text">${text}</div>
        <div class="message-time">${new Date().toLocaleTimeString()}</div>
        ${
          citations && citations.length > 0
            ? `
            <div class="citations">
              <div class="citations-title">� Fontes:</div>
              ${citations
                .map(
                  (citation) => `
                  <div class="citation">
                    <div class="citation-source">${
                      citation.source || "Documento"
                    }</div>
                    <div class="citation-content">${
                      citation.content || ""
                    }</div>
                    ${
                      citation.relevance
                        ? `<div class="citation-relevance">Relevância: ${Math.round(
                            citation.relevance * 100
                          )}%</div>`
                        : ""
                    }
                  </div>
                `
                )
                .join("")}
            </div>
        `
            : ""
        }
      </div>
    </div>
  `;

  // Remove empty state if it exists
  const emptyState = chatMessages.querySelector(".empty-state");
  if (emptyState) {
    emptyState.remove();
  }

  chatMessages.insertAdjacentHTML("beforeend", messageHtml);
  chatMessages.scrollTop = chatMessages.scrollHeight;

  return messageId;
}

// Remove message
function removeMessage(messageId) {
  const messageElement = document.getElementById(messageId);
  if (messageElement) {
    messageElement.remove();
  }
}

// Format file size
function formatFileSize(bytes) {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
}

// Auto-refresh documents every 5 seconds
setInterval(() => {
  if (documents.some((d) => d.status === "PROCESSING")) {
    loadDocuments();
  }
}, 5000);

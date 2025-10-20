package villanidev.ai.chatbot.rag.service;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import villanidev.ai.chatbot.rag.model.DocumentMetadata;
import villanidev.ai.chatbot.rag.repository.DocumentMetadataRepository;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AdvancedDocumentProcessorService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final TokenTextSplitter textSplitter;
    private final Tika tika;

    // Supported file types
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "application/pdf",
        "text/plain", "text/csv",
        "application/msword", 
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "image/jpeg", "image/png", "image/tiff", "image/bmp"
    );

    public AdvancedDocumentProcessorService(DocumentMetadataRepository documentMetadataRepository) {
        this.documentMetadataRepository = documentMetadataRepository;
        this.textSplitter = new TokenTextSplitter();
        this.tika = new Tika();
    }

    public List<Document> processDocument(MultipartFile file, DocumentMetadata metadata) throws IOException {
        log.info("Processing document: {} (type: {})", file.getOriginalFilename(), file.getContentType());
        
        String contentType = detectContentType(file);
        log.debug("Detected content type: {}", contentType);

        List<Document> documents;

        try {
            switch (contentType) {
                case "application/pdf":
                    documents = processPdf(file, metadata);
                    break;
                case "text/plain":
                    documents = processTextFile(file, metadata);
                    break;
                case "text/csv":
                    documents = processCsvFile(file, metadata);
                    break;
                case "application/msword":
                    documents = processDocFile(file, metadata);
                    break;
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                    documents = processDocxFile(file, metadata);
                    break;
                case "application/vnd.ms-excel":
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                    documents = processExcelFile(file, metadata);
                    break;
                case "image/jpeg":
                case "image/png":
                case "image/tiff":
                case "image/bmp":
                    documents = processImageFile(file, metadata);
                    break;
                default:
                    // Fallback to Tika for unknown types
                    documents = processWithTika(file, metadata);
            }

            metadata.setChunkCount(documents.size());
            metadata.setStatus("PROCESSING");
            documentMetadataRepository.save(metadata);

            log.info("Successfully processed {} into {} chunks", file.getOriginalFilename(), documents.size());
            return documents;

        } catch (Exception e) {
            log.error("Error processing document: {}", file.getOriginalFilename(), e);
            metadata.setStatus("ERROR");
            metadata.setErrorMessage(e.getMessage());
            documentMetadataRepository.save(metadata);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private String detectContentType(MultipartFile file) {
        // Try file extension first
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = filename.toLowerCase();
            if (extension.endsWith(".pdf")) return "application/pdf";
            if (extension.endsWith(".txt")) return "text/plain";
            if (extension.endsWith(".csv")) return "text/csv";
            if (extension.endsWith(".doc")) return "application/msword";
            if (extension.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (extension.endsWith(".xls")) return "application/vnd.ms-excel";
            if (extension.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) return "image/jpeg";
            if (extension.endsWith(".png")) return "image/png";
            if (extension.endsWith(".tiff") || extension.endsWith(".tif")) return "image/tiff";
            if (extension.endsWith(".bmp")) return "image/bmp";
        }

        // Fallback to provided content type
        String contentType = file.getContentType();
        return contentType != null ? contentType : "application/octet-stream";
    }

    private List<Document> processPdf(MultipartFile file, DocumentMetadata metadata) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes());

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
            resource,
            PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageBottomMargin(0)
                .build()
        );

        List<Document> pages = pdfReader.get();

        pages.forEach(doc -> {
            doc.getMetadata().put("source", metadata.getFilename());
            doc.getMetadata().put("document_id", metadata.getId().toString());
            doc.getMetadata().put("content_type", "pdf");
            doc.getMetadata().put("page", doc.getMetadata().get("page_number"));
        });

        return textSplitter.apply(pages);
    }

    private List<Document> processTextFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        String content = new String(file.getBytes(), "UTF-8");

        Document doc = new Document(
            content,
            Map.of(
                "source", metadata.getFilename(),
                "document_id", metadata.getId().toString(),
                "content_type", "text"
            )
        );

        return textSplitter.apply(List.of(doc));
    }

    private List<Document> processCsvFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        List<Document> documents = new ArrayList<>();
        int totalRows = 0;
        
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {
            
            String[] headers = null;
            String[] row;
            int rowNumber = 0;
            
            try {
                while ((row = csvReader.readNext()) != null) {
                rowNumber++;
                totalRows = rowNumber;
                
                if (rowNumber == 1) {
                    headers = row;
                    continue; // Skip header row
                }
                
                // Convert row to text representation
                StringBuilder rowText = new StringBuilder();
                for (int i = 0; i < row.length && i < (headers != null ? headers.length : row.length); i++) {
                    if (headers != null && i < headers.length) {
                        rowText.append(headers[i]).append(": ").append(row[i]).append("\n");
                    } else {
                        rowText.append("Column ").append(i + 1).append(": ").append(row[i]).append("\n");
                    }
                }
                
                Document doc = new Document(
                    rowText.toString(),
                    Map.of(
                        "source", metadata.getFilename(),
                        "document_id", metadata.getId().toString(),
                        "content_type", "csv",
                        "row_number", String.valueOf(rowNumber)
                    )
                );
                
                documents.add(doc);
                
                // Process in batches to avoid memory issues
                if (rowNumber % 100 == 0) {
                    log.debug("Processed {} rows from CSV", rowNumber);
                }
                }
            } catch (Exception e) {
                log.error("Error processing CSV file", e);
                throw new IOException("CSV processing failed", e);
            }
        }
        
        log.info("Processed CSV with {} rows", totalRows - 1);
        return documents;
    }

    private List<Document> processDocFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            
            String content = extractor.getText();
            
            Document doc = new Document(
                content,
                Map.of(
                    "source", metadata.getFilename(),
                    "document_id", metadata.getId().toString(),
                    "content_type", "doc"
                )
            );
            
            return textSplitter.apply(List.of(doc));
        }
    }

    private List<Document> processDocxFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String content = extractor.getText();
            
            Document doc = new Document(
                content,
                Map.of(
                    "source", metadata.getFilename(),
                    "document_id", metadata.getId().toString(),
                    "content_type", "docx"
                )
            );
            
            return textSplitter.apply(List.of(doc));
        }
    }

    private List<Document> processExcelFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        List<Document> documents = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = null;
            
            try {
                // Try XLSX first
                workbook = new XSSFWorkbook(inputStream);
            } catch (Exception e) {
                // Fallback to XLS
                try (InputStream retryStream = file.getInputStream()) {
                    workbook = new HSSFWorkbook(retryStream);
                }
            }
            
            if (workbook != null) {
                for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    String sheetName = sheet.getSheetName();
                    
                    StringBuilder sheetContent = new StringBuilder();
                    Row headerRow = null;
                    
                    for (Row row : sheet) {
                        if (headerRow == null) {
                            headerRow = row;
                            continue;
                        }
                        
                        StringBuilder rowText = new StringBuilder();
                        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                            Cell cell = row.getCell(cellIndex);
                            Cell headerCell = headerRow.getCell(cellIndex);
                            
                            String header = headerCell != null ? getCellValueAsString(headerCell) : "Column " + (cellIndex + 1);
                            String value = cell != null ? getCellValueAsString(cell) : "";
                            
                            if (!value.trim().isEmpty()) {
                                rowText.append(header).append(": ").append(value).append("\n");
                            }
                        }
                        
                        if (rowText.length() > 0) {
                            sheetContent.append(rowText);
                            
                            // Create document for each row to allow better chunking
                            Document doc = new Document(
                                rowText.toString(),
                                Map.of(
                                    "source", metadata.getFilename(),
                                    "document_id", metadata.getId().toString(),
                                    "content_type", "excel",
                                    "sheet_name", sheetName,
                                    "row_number", String.valueOf(row.getRowNum())
                                )
                            );
                            documents.add(doc);
                        }
                    }
                }
                workbook.close();
            }
        }
        
        return documents;
    }

    private List<Document> processImageFile(MultipartFile file, DocumentMetadata metadata) throws IOException {
        // For now, create a placeholder document for images
        // In a real implementation, you would use OCR libraries like Tesseract
        
        String content = String.format(
            "Image file: %s\nType: %s\nSize: %d bytes\n\n[OCR processing would be implemented here with Tesseract or similar library]",
            metadata.getFilename(),
            file.getContentType(),
            file.getSize()
        );
        
        Document doc = new Document(
            content,
            Map.of(
                "source", metadata.getFilename(),
                "document_id", metadata.getId().toString(),
                "content_type", "image",
                "requires_ocr", "true"
            )
        );
        
        return List.of(doc);
    }

    private List<Document> processWithTika(MultipartFile file, DocumentMetadata metadata) throws IOException {
        try {
            String content = tika.parseToString(file.getInputStream());
            
            Document doc = new Document(
                content,
                Map.of(
                    "source", metadata.getFilename(),
                    "document_id", metadata.getId().toString(),
                    "content_type", "tika_processed",
                    "original_type", file.getContentType()
                )
            );
            
            return textSplitter.apply(List.of(doc));
            
        } catch (TikaException e) {
            throw new IOException("Tika parsing failed", e);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    public boolean isFileTypeSupported(String contentType) {
        return SUPPORTED_TYPES.contains(contentType);
    }

    public Set<String> getSupportedFileTypes() {
        return new HashSet<>(SUPPORTED_TYPES);
    }

    // Existing methods for compatibility
    public void markAsCompleted(Long documentId, int chunksProcessed) {
        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));

        metadata.setStatus("COMPLETED");
        metadata.setChunkCount(chunksProcessed);
        metadata.setProcessedAt(LocalDateTime.now());
        documentMetadataRepository.save(metadata);
    }

    public void markAsFailed(Long documentId, String errorMessage) {
        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));

        metadata.setStatus("FAILED");
        metadata.setErrorMessage(errorMessage);
        metadata.setProcessedAt(LocalDateTime.now());
        documentMetadataRepository.save(metadata);
    }

    public void removeDocumentFromVectorStore(Long documentId) {
        // TODO: Implement vector store document removal
        // This would require integration with VectorStoreService
    }

    public void clearVectorStore() {
        // TODO: Implement vector store clearing
        // This would require integration with VectorStoreService
    }
}
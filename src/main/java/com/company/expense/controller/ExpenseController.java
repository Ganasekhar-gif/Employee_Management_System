package com.company.expense.controller;

import com.company.expense.dto.request.ExpenseRequest;
import com.company.expense.dto.request.ReceiptUpload;
import com.company.expense.dto.response.ExpenseResponse;
import com.company.expense.dto.response.ExpenseSummaryResponse;
import com.company.expense.dto.response.ReceiptDownload;
import com.company.expense.enums.ExpenseStatus;
import com.company.expense.security.AuthenticatedUser;
import com.company.expense.service.ExpenseService;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse created = expenseService.createExpense(principal.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<ExpenseSummaryResponse> listOwnExpenses(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) ExpenseStatus status) {
        return expenseService.listOwnExpenses(principal.id(), status);
    }

    @GetMapping("/{id}")
    public ExpenseResponse getExpense(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return expenseService.getExpense(principal.id(), id);
    }

    @PutMapping("/{id}")
    public ExpenseResponse updateExpense(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request) {
        return expenseService.updateExpense(principal.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        expenseService.deleteExpense(principal.id(), id);
    }

    @PostMapping(path = "/{id}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse.ReceiptInfo uploadReceipt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return expenseService.uploadReceipt(principal.id(), id,
                new ReceiptUpload(file.getOriginalFilename(), file.getSize(), file));
    }

    @GetMapping("/{id}/receipts/{receiptId}")
    public ResponseEntity<Resource> downloadReceipt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @PathVariable Long receiptId) {
        ReceiptDownload download = expenseService.downloadReceipt(principal.id(), id, receiptId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @DeleteMapping("/{id}/receipts/{receiptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReceipt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @PathVariable Long receiptId) {
        expenseService.removeReceipt(principal.id(), id, receiptId);
    }

    @PostMapping("/{id}/submit")
    public ExpenseResponse submitExpense(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return expenseService.submitExpense(principal.id(), id);
    }
}

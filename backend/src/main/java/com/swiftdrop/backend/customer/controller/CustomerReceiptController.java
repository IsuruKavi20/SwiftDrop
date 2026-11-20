package com.swiftdrop.backend.customer.controller;
import com.swiftdrop.backend.customer.service.CustomerReceiptService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/me/deliveries")
@PreAuthorize("hasAuthority('parcel:read-own')")
class CustomerReceiptController {
    private final CustomerReceiptService receipts;
    CustomerReceiptController(CustomerReceiptService receipts) { this.receipts = receipts; }
    @GetMapping(value="/{id}/receipt", produces=MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> getReceipt(@PathVariable Long id, @AuthenticationPrincipal Jwt user) {
        byte[] pdf=receipts.pdf(CurrentUser.id(user),id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=SwiftDrop-receipt-"+id+".pdf")
                .contentLength(pdf.length).body(pdf);
    }
}

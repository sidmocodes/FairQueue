package com.fairqueue.admission.controller;

import com.fairqueue.admission.service.AdmissionService;
import com.fairqueue.common.dto.AdmissionClaimRequest;
import com.fairqueue.common.dto.AdmissionClaimResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admission")
public class AdmissionController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdmissionController.class);
    
    private final AdmissionService admissionService;
    
    public AdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }
    
    @PostMapping("/claim")
    public ResponseEntity<?> claimAdmission(@Valid @RequestBody AdmissionClaimRequest request) {
        logger.info("Admission claim request received");
        
        AdmissionClaimResponse response = admissionService.claimAdmission(request.getQueueToken());
        
        logger.info("Admission pass issued: passId={}", response.getAdmissionPass());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/validate/{passId}")
    public ResponseEntity<?> validatePass(@PathVariable String passId) {
        boolean valid = admissionService.validatePass(passId);
        
        if (valid) {
            return ResponseEntity.ok().body("{\"valid\": true}");
        } else {
            return ResponseEntity.badRequest().body("{\"valid\": false}");
        }
    }
}

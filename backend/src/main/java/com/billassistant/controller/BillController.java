package com.billassistant.controller;

import com.billassistant.auth.UserContext;
import com.billassistant.model.Bill;
import com.billassistant.dto.CreateBillRequest;
import com.billassistant.service.BillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping
    public List<Bill> findAll() {
        return billService.findAll(UserContext.requireUid());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Bill create(@Valid @RequestBody CreateBillRequest request) {
        return billService.create(UserContext.requireUid(), request);
    }
}

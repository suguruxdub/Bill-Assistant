package com.billassistant.service;

import com.billassistant.dao.BillMapper;
import com.billassistant.dto.CreateBillRequest;
import com.billassistant.model.Bill;
import com.billassistant.model.BillEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BillService {

    private final BillMapper billMapper;

    public BillService(BillMapper billMapper) {
        this.billMapper = billMapper;
    }

    @Transactional(readOnly = true)
    public List<Bill> findAll(long uid) {
        return billMapper.findAll(uid).stream().map(this::toBill).toList();
    }

    @Transactional
    public Bill create(long uid, CreateBillRequest request) {
        BillEntity entity = new BillEntity();
        entity.setUserId(uid);
        entity.setTitle(request.title());
        entity.setAmount(request.amount());
        entity.setBillingDate(request.billingDate());
        entity.setCategory(request.category());
        entity.setPaid(request.paid());
        billMapper.insert(entity);
        return toBill(entity);
    }

    private Bill toBill(BillEntity entity) {
        return new Bill(entity.getId(), entity.getTitle(), entity.getAmount(),
                entity.getBillingDate(), entity.getCategory(), entity.isPaid());
    }
}

package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.infra.service.TerminalQrService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/terminal")
public class TerminalQrController {
    @Autowired private TerminalQrService service;
    @PostMapping("/customer-link/consume") public CustomerLinkConsumeResponse consume(@RequestHeader(value="X-Terminal-Token",required=false) String credential,@Valid @RequestBody CustomerLinkConsumeRequest request){return service.consumeCustomer(credential,request);}
    @PostMapping("/coupons/apply") public CouponApplyResponse apply(@RequestHeader(value="X-Terminal-Token",required=false) String credential,@Valid @RequestBody CouponApplyRequest request){return service.applyCoupon(credential,request);}
}

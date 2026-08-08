package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.policy.SecurityPolicy;
import com.sentinelgateway.gateway.policy.SecurityPolicyRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/admin/policies")
public class PolicyAdminController {

    private final SecurityPolicyRepository repository;

    public PolicyAdminController(SecurityPolicyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Flux<SecurityPolicy> listPolicies() {
        return repository.findAll();
    }
}

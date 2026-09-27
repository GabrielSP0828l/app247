package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.CustomerIdentity;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.infra.repository.CustomerIdentityRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerIdentityService {
    private final CustomerIdentityRepository identities;
    private final EmpresaRepository empresas;
    private final CpfService cpfs;
    private final CustomerIdentityHashService hashes;

    public CustomerIdentityService(CustomerIdentityRepository identities, EmpresaRepository empresas,
                                   CpfService cpfs, CustomerIdentityHashService hashes) {
        this.identities = identities;
        this.empresas = empresas;
        this.cpfs = cpfs;
        this.hashes = hashes;
    }

    @Transactional
    public CustomerIdentity resolve(Empresa empresa, String rawCpf) {
        String cpfHash = hashes.hashCpf(cpfs.requireValid(rawCpf));
        Empresa locked = empresas.findByIdForUpdate(empresa.getId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa não encontrada"));
        return identities.findByEmpresaIdAndCpfHash(locked.getId(), cpfHash).orElseGet(() -> {
            CustomerIdentity identity = new CustomerIdentity();
            identity.setEmpresa(locked);
            identity.setCpfHash(cpfHash);
            return identities.save(identity);
        });
    }
}

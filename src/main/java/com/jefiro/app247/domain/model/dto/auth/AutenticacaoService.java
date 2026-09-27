package com.jefiro.app247.domain.model.dto.auth;

import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.CpfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UserRepository repository;
    @Autowired
    private CpfService cpfs;

    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        UserDetails user = repository.findByCpf(cpfs.requireValid(cpf));
        if (user == null) {
            throw new UsernameNotFoundException("Usuário não encontrado com o CPF: " + cpf);
        }
        return user;
    }
}

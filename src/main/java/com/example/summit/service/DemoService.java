package com.example.summit.service;

import com.example.summit.data.ServerInfoRepository;
import com.example.summit.domain.ServerInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer — owns <em>load and commit</em>, the transactional boundary. For
 * the real POC this becomes the whole-graph save under the {@code ORA_ROWSCN} check
 * (the Forms {@code COMMIT_FORM} gate). Here it is a read-only load.
 */
@Service
public class DemoService {

    private final ServerInfoRepository repository;

    public DemoService(ServerInfoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ServerInfo loadServerInfo() {
        return repository.fetchServerInfo();
    }
}

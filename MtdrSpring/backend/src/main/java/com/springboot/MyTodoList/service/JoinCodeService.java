package com.springboot.MyTodoList.service;

import com.springboot.MyTodoList.model.AppUser;
import com.springboot.MyTodoList.model.JoinCode;
import com.springboot.MyTodoList.repository.AppUserRepository;
import com.springboot.MyTodoList.repository.JoinCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class JoinCodeService {

    private static final Logger log = LoggerFactory.getLogger(JoinCodeService.class);

    private static final int EXPIRY_MINUTES = 15;

    @Autowired
    private JoinCodeRepository joinCodeRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Transactional
    public String generateCode(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        String code = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        JoinCode joinCode = new JoinCode();
        joinCode.setJoinCode(code);
        joinCode.setUser(user);
        joinCode.setCreatedAt(LocalDateTime.now());
        joinCode.setExpiration(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));

        joinCodeRepository.save(joinCode);

        return code;
    }

    @Transactional
    public AppUser linkTelegramAccount(String code, Long telegramChatId, String telegramUsername) {
        log.info("Linking Telegram chatId {} with code {}", telegramChatId, code);

        JoinCode joinCode = joinCodeRepository.findByJoinCode(code)
                .orElseThrow(() -> new RuntimeException("Code not found"));

        if (!joinCode.isValid()) {
            throw new RuntimeException("Code expired or already used");
        }

        joinCode.setUsedAt(LocalDateTime.now());
        joinCodeRepository.save(joinCode);

        AppUser user = joinCode.getUser();

        user.setTelegramChatId(telegramChatId);
        user.setTelegramUsername(telegramUsername);
        user.setTelegramConnected(true);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser getUserByTelegramChatId(Long telegramChatId) {
        return userRepository.findByTelegramChatId(telegramChatId)
                .orElseThrow(() -> new RuntimeException("Telegram account is not linked. Use /ConfigUser first."));
    }
}
package com.platform.maintenance.service;

import com.platform.maintenance.config.JwtTokenProvider;
import com.platform.maintenance.dto.AuthDtos;
import com.platform.maintenance.model.User;
import com.platform.maintenance.model.Workspace;
import com.platform.maintenance.model.WorkspaceMember;
import com.platform.maintenance.model.enums.Role;
import com.platform.maintenance.repository.UserRepository;
import com.platform.maintenance.repository.WorkspaceMemberRepository;
import com.platform.maintenance.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);

        // Auto-create a default personal workspace for the new user
        String slug = user.getName().toLowerCase().replaceAll("[^a-z0-9]", "-") + "-workspace";
        if (workspaceRepository.existsBySlug(slug)) {
            slug = slug + "-" + user.getId();
        }

        Workspace workspace = Workspace.builder()
                .name(user.getName() + "'s Workspace")
                .slug(slug)
                .owner(user)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(Role.OWNER)
                .build();
        workspaceMemberRepository.save(member);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return AuthDtos.AuthResponse.builder()
                .token(token)
                .user(mapToDto(user))
                .build();
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return AuthDtos.AuthResponse.builder()
                .token(token)
                .user(mapToDto(user))
                .build();
    }

    public AuthDtos.UserDto mapToDto(User user) {
        return AuthDtos.UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .githubUsername(user.getGithubUsername())
                .build();
    }
}

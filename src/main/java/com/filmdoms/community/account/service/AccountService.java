package com.filmdoms.community.account.service;

import com.filmdoms.community.account.config.jwt.JwtTokenProvider;
import com.filmdoms.community.account.data.constant.AccountRole;
import com.filmdoms.community.account.data.dto.AccountDto;
import com.filmdoms.community.account.data.dto.request.DeleteAccountRequestDto;
import com.filmdoms.community.account.data.dto.request.JoinRequestDto;
import com.filmdoms.community.account.data.dto.request.UpdatePasswordRequestDto;
import com.filmdoms.community.account.data.dto.request.UpdateProfileRequestDto;
import com.filmdoms.community.account.data.dto.response.AccountResponseDto;
import com.filmdoms.community.account.data.dto.response.LoginResponseDto;
import com.filmdoms.community.account.data.dto.response.RefreshAccessTokenResponseDto;
import com.filmdoms.community.account.data.entity.Account;
import com.filmdoms.community.account.exception.ApplicationException;
import com.filmdoms.community.account.exception.ErrorCode;
import com.filmdoms.community.account.repository.AccountRepository;
import com.filmdoms.community.account.repository.RefreshTokenRepository;
import com.filmdoms.community.file.data.entity.File;
import com.filmdoms.community.file.repository.FileRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountService {
    private final FileRepository fileRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponseDto login(String email, String password) {

        log.info("가입 여부 확인");
        AccountDto accountDto = accountRepository.findByEmail(email)
                .map(AccountDto::from)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        log.info("비밀번호를 암호화 시켜 저장된 비밀번호와 대조");
        if (!passwordEncoder.matches(password, accountDto.getPassword())) {
            throw new ApplicationException(ErrorCode.INVALID_PASSWORD);
        }

        log.info("저장된 토큰 존재 여부 확인, 없다면 생성");
        String key = UUID.nameUUIDFromBytes(email.getBytes()).toString();
        String refreshToken = refreshTokenRepository.findByKey(key)
                .orElseGet(() -> jwtTokenProvider.createRefreshToken(key));

        log.info("리프레시 토큰 저장 / 갱신");
        refreshTokenRepository.save(key, refreshToken);

        return LoginResponseDto.builder()
                .accessToken(jwtTokenProvider.createAccessToken(String.valueOf(accountDto.getId())))
                .refreshToken(refreshToken)
                .build();
    }

    public RefreshAccessTokenResponseDto refreshAccessToken(String refreshToken) {

        log.info("토큰 내 저장된 키 추출");
        String key = jwtTokenProvider.getSubject(refreshToken);

        log.info("키로 저장된 토큰 호출");
        String savedToken = refreshTokenRepository.findByKey(key)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TOKEN_NOT_IN_DB));

        log.info("저장된 토큰과 대조");
        if (!Objects.equals(savedToken, refreshToken)) {
            throw new ApplicationException(ErrorCode.INVALID_TOKEN);
        }

        log.info("리프레시 토큰 갱신");
        refreshTokenRepository.save(key, refreshToken);

        log.info("새로운 엑세스 토큰 발급");
        String accessToken = jwtTokenProvider.createAccessToken(key);
        return RefreshAccessTokenResponseDto.builder()
                .accessToken(accessToken)
                .build();
    }

    public void logout(String refreshToken) {

        log.info("토큰 내 저장된 키 추출");
        String key = jwtTokenProvider.getSubject(refreshToken);

        log.info("키로 저장된 토큰 호출");
        String savedToken = refreshTokenRepository.findByKey(key)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TOKEN_NOT_IN_DB));

        log.info("저장된 토큰과 대조");
        if (!Objects.equals(savedToken, refreshToken)) {
            throw new ApplicationException(ErrorCode.INVALID_TOKEN);
        }

        log.info("저장된 토큰 삭제");
        refreshTokenRepository.deleteByKey(key);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return accountRepository.existsByNickname(nickname);
    }

    public boolean isEmailDuplicate(String email) {
        return accountRepository.existsByEmail(email);
    }

    public void createAccount(JoinRequestDto requestDto) {

        log.info("닉네임 중복 확인");
        if (isNicknameDuplicate(requestDto.getNickname())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_NICKNAME);
        }

        log.info("이메일 중복 확인");
        if (isEmailDuplicate(requestDto.getEmail())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_EMAIL);
        }

        log.info("Account 엔티티 생성");
        Account newAccount = Account.builder()
                //.username(requestDto.getUsername())
                .password(passwordEncoder.encode(requestDto.getPassword()))
                .nickname(requestDto.getNickname())
                .email(requestDto.getEmail())
                .role(AccountRole.USER)
                .profileImage(getDefaultImage())
                .build();

        log.info("Account 엔티티 저장");
        accountRepository.save(newAccount);
    }

    // TODO: 프로필 기본 이미지 어떻게 처리할 지 상의 필요
    private File getDefaultImage() {
        return fileRepository.findById(1L).orElseGet(() -> fileRepository.save(
                        File.builder()
                                .uuidFileName("7f5fb6d2-40fa-4e3d-81e6-a013af6f4f23.png")
                                .originalFileName("original_file_name")
                                .build()
                )
        );
    }

    public AccountResponseDto readAccount(AccountDto accountDto) {

        log.info("Account 엔티티 호출");
        Account account = accountRepository.findByEmailWithImage(accountDto.getEmail())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        return AccountResponseDto.from(account);
    }

    @Transactional
    public AccountResponseDto updateAccountProfile(UpdateProfileRequestDto requestDto, AccountDto accountDto) {

        log.info("Account 엔티티 호출");
        Account account = accountRepository.findByEmailWithImage(accountDto.getEmail())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        File profileImage = account.getProfileImage();
        if (!Objects.equals(requestDto.getFileId(), profileImage.getId())) {
            log.info("요청 File 엔티티 호출");
            profileImage = fileRepository.findById(requestDto.getFileId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_IMAGE_ID));
        }

        log.info("Account 엔티티 수정");
        account.updateProfile(requestDto.getNickname(), profileImage);

        return AccountResponseDto.from(account);
    }

    @Transactional
    public void updateAccountPassword(UpdatePasswordRequestDto requestDto, AccountDto accountDto) {

        log.info("Account 엔티티 호출");
        Account account = accountRepository.findByEmail(accountDto.getEmail())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        log.info("기존 비밀번호와 대조");
        if (!passwordEncoder.matches(requestDto.getOldPassword(), account.getPassword())) {
            throw new ApplicationException(ErrorCode.INVALID_PASSWORD);
        }

        log.info("비밀번호 수정");
        account.updatePassword(passwordEncoder.encode(requestDto.getNewPassword()));
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequestDto requestDto, AccountDto accountDto) {

        log.info("Account 엔티티 호출");
        Account account = accountRepository.findByEmail(accountDto.getEmail())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        log.info("기존 비밀번호와 대조");
        if (!passwordEncoder.matches(requestDto.getPassword(), account.getPassword())) {
            throw new ApplicationException(ErrorCode.INVALID_PASSWORD);
        }

        log.info("Account 엔티티 삭제");
        accountRepository.delete(account);
    }
}

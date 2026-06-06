package com.scenic.ai.modules.app.user.service;

import com.scenic.ai.modules.app.user.dto.LoginResponse;
import com.scenic.ai.modules.app.user.dto.UserInfoDto;
import com.scenic.ai.modules.app.user.dto.UserLoginRequest;
import com.scenic.ai.modules.app.user.dto.UserRegisterRequest;
import com.scenic.ai.modules.app.user.entity.TouristUser;
import com.scenic.ai.modules.app.user.mapper.TouristUserMapper;
import com.scenic.ai.modules.app.user.util.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AppUserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final TouristUserMapper touristUserMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AppUserService(TouristUserMapper touristUserMapper, JwtUtil jwtUtil) {
        this.touristUserMapper = touristUserMapper;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public LoginResponse register(UserRegisterRequest request) {
        String account = request.getAccountText();
        String nickname = request.getNicknameText();
        String password = request.password;

        // 1. 账号校验
        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入账号");
        }
        if (account.length() < 3 || account.length() > 30) {
            throw new IllegalArgumentException("账号长度应为3-30个字符");
        }

        // 2. 昵称校验（增加）
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入昵称");
        }
        if (nickname.length() > 100) {
            throw new IllegalArgumentException("昵称过长");
        }

        // 3. 密码校验
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }

        // 4. 检查账号是否已存在
        TouristUser exists = touristUserMapper.selectByLoginAccount(account);
        if (exists != null) {
            throw new IllegalArgumentException("账号已存在");
        }

        // 5. 构建用户对象
        TouristUser user = new TouristUser();
        user.userId = generateUserId();
        user.loginAccount = account;
        user.nickname = nickname;
        user.phone = isPhone(account) ? account : null;
        user.passwordHash = passwordEncoder.encode(password);
        user.registerSource = "VISITOR_APP";
        user.gpsAuthorized = 0;
        user.profileCompleted = 0;
        user.status = 1;
        user.deleted = 0;

        // 6. 插入数据库
        touristUserMapper.insertTouristUser(user);
        touristUserMapper.insertProfileIfAbsent(user.userId);

        // 7. 查询刚插入的用户并生成 token
        TouristUser savedUser = touristUserMapper.selectByUserId(user.userId);
        String token = jwtUtil.generateToken(savedUser.userId);

        return new LoginResponse(token, UserInfoDto.from(savedUser));
    }

    public LoginResponse login(UserLoginRequest request) {
        String account = request.getAccountText();
        String password = request.password;

        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入账号");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入密码");
        }

        TouristUser user = touristUserMapper.selectByLoginAccount(account);

        if (user == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        if (user.status != null && user.status == 0) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (user.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        touristUserMapper.updateLoginTime(user.userId);

        TouristUser latestUser = touristUserMapper.selectByUserId(user.userId);
        String token = jwtUtil.generateToken(latestUser.userId);

        return new LoginResponse(token, UserInfoDto.from(latestUser));
    }

    public UserInfoDto getProfileByToken(String authorization) {
        String userId = jwtUtil.parseUserId(authorization);

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }

        TouristUser user = touristUserMapper.selectByUserId(userId);

        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        return UserInfoDto.from(user);
    }

    public UserInfoDto getUserInfoByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        TouristUser user = touristUserMapper.selectByUserId(userId.trim());
        return user == null ? null : UserInfoDto.from(user);
    }

    public String resolveRequiredUserId(String authorization, String providedUserId) {
        String tokenUserId = firstNotBlank(jwtUtil.parseUserId(authorization));
        if (!tokenUserId.isEmpty()) {
            return requireExistingLoginUser(tokenUserId);
        }

        String userId = firstNotBlank(providedUserId);
        if (userId.isEmpty() || isTemporaryUserId(userId)) {
            throw new IllegalArgumentException("请先登录");
        }

        return requireExistingLoginUser(userId);
    }

    private String requireExistingLoginUser(String userId) {
        TouristUser user = touristUserMapper.selectByUserId(userId);
        if (user == null || (user.status != null && user.status == 0)) {
            throw new IllegalArgumentException("请先登录");
        }
        return user.userId;
    }

    private String generateUserId() {
        return "tourist_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private boolean isPhone(String account) {
        return account != null && PHONE_PATTERN.matcher(account).matches();
    }

    private boolean isTemporaryUserId(String userId) {
        if (userId == null) {
            return true;
        }

        String value = userId.trim().toLowerCase();
        return value.isEmpty()
                || "anonymous".equals(value)
                || value.startsWith("tourist_")
                || value.startsWith("visitor_")
                || value.startsWith("android-live2d-");
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }
}

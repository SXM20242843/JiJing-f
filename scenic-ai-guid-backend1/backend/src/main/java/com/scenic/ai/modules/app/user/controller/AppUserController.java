package com.scenic.ai.modules.app.user.controller;

import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.dto.FavoriteRequest;
import com.scenic.ai.modules.app.user.dto.LoginResponse;
import com.scenic.ai.modules.app.user.dto.UserInfoDto;
import com.scenic.ai.modules.app.user.dto.UserLoginRequest;
import com.scenic.ai.modules.app.user.dto.UserProfileRequest;
import com.scenic.ai.modules.app.user.dto.UserProfileResponse;
import com.scenic.ai.modules.app.user.dto.UserRegisterRequest;
import com.scenic.ai.modules.app.user.dto.WechatBindRequest;
import com.scenic.ai.modules.app.user.dto.WechatBindResponse;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import com.scenic.ai.modules.app.user.service.FavoriteService;
import com.scenic.ai.modules.app.user.service.UserProfileService;
import com.scenic.ai.modules.app.user.service.WechatBindService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class AppUserController {

    private final AppUserService appUserService;
    private final BehaviorEventService behaviorEventService;
    private final FavoriteService favoriteService;
    private final UserProfileService userProfileService;
    private final WechatBindService wechatBindService;

    public AppUserController(
            AppUserService appUserService,
            BehaviorEventService behaviorEventService,
            FavoriteService favoriteService,
            UserProfileService userProfileService,
            WechatBindService wechatBindService
    ) {
        this.appUserService = appUserService;
        this.behaviorEventService = behaviorEventService;
        this.favoriteService = favoriteService;
        this.userProfileService = userProfileService;
        this.wechatBindService = wechatBindService;
    }

    @PostMapping("/register")
    public ApiResult<LoginResponse> register(@RequestBody UserRegisterRequest request) {
        try {
            return ApiResult.ok(appUserService.register(request));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("注册失败，请稍后重试");
        }
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody UserLoginRequest request) {
        try {
            return ApiResult.ok(appUserService.login(request));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("登录失败，请稍后重试");
        }
    }

    @GetMapping("/profile")
    public ApiResult<UserInfoDto> profile(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        try {
            return ApiResult.ok(appUserService.getProfileByToken(authorization));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("获取用户信息失败");
        }
    }

    @GetMapping(value = "/profile", params = "userId")
    public ApiResult<UserProfileResponse> profileByUserId(@RequestParam("userId") String userId) {
        try {
            return ApiResult.ok(userProfileService.getProfile(userId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("获取用户画像失败");
        }
    }

    @PostMapping("/profile/save")
    public ApiResult<UserProfileResponse> saveProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) UserProfileRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            return ApiResult.ok(userProfileService.saveProfile(request, userId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("保存用户画像失败");
        }
    }

    @PostMapping("/wechat/bind")
    public ApiResult<WechatBindResponse> bindWechat(@RequestBody(required = false) WechatBindRequest request) {
        try {
            return ApiResult.ok(wechatBindService.bindWechat(request));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("微信绑定失败，请稍后重试");
        }
    }

    @PostMapping("/logout")
    public ApiResult<Boolean> logout() {
        return ApiResult.ok(true);
    }

    @PostMapping("/behavior/add")
    public ApiResult<Map<String, Object>> addBehavior(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody BehaviorEventRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            String eventId = behaviorEventService.addBehaviorEvent(request, userId);

            Map<String, Object> data = new HashMap<>();
            data.put("event_id", eventId);

            return ApiResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("行为上报失败");
        }
    }

    @PostMapping("/favorite/add")
    public ApiResult<Boolean> addFavorite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody FavoriteRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            return ApiResult.ok(favoriteService.addFavorite(request, userId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("收藏失败");
        }
    }

    @PostMapping("/favorite/remove")
    public ApiResult<Boolean> removeFavorite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody FavoriteRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            return ApiResult.ok(favoriteService.removeFavorite(request, userId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("取消收藏失败");
        }
    }

    @PostMapping("/favorite/status")
    public ApiResult<Map<String, Object>> favoriteStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody FavoriteRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            if (request != null) {
                request.userId = userId;
            }
            boolean favorite = favoriteService.isFavorite(request);

            Map<String, Object> data = new HashMap<>();
            data.put("favorite", favorite);

            return ApiResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("查询收藏状态失败");
        }
    }

    @GetMapping("/favorite/list")
    public ApiResult<?> favoriteList(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "user_id", required = false) String userId
    ) {
        try {
            String resolvedUserId = appUserService.resolveRequiredUserId(authorization, userId);
            return ApiResult.ok(favoriteService.listFavorites(resolvedUserId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("获取收藏列表失败");
        }
    }
}

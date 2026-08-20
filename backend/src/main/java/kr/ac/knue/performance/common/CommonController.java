package kr.ac.knue.performance.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import kr.ac.knue.performance.api.ApiEnvelope;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonController {
    private final CommonService service;
    public CommonController(CommonService service) { this.service = service; }

    @GetMapping("/api/health")
    public Map<String,Object> health() { return ApiEnvelope.ok(Map.of("status", "UP")); }

    @PostMapping("/api/auth/login")
    public Map<String,Object> login(@RequestBody Map<String,Object> body, HttpServletResponse response) {
        String sid = service.login(String.valueOf(body.getOrDefault("loginId", "")), String.valueOf(body.getOrDefault("password", "")));
        Cookie cookie = new Cookie("SESSION", sid); cookie.setHttpOnly(true); cookie.setPath("/"); cookie.setMaxAge(8 * 60 * 60); response.addCookie(cookie);
        return ApiEnvelope.ok(Map.of("sessionId", sid));
    }

    @PostMapping("/api/auth/logout")
    public Map<String,Object> logout(@CookieValue(value="SESSION", required=false) String sessionId, HttpServletResponse response) {
        service.logout(sessionId);
        Cookie cookie = new Cookie("SESSION", ""); cookie.setHttpOnly(true); cookie.setPath("/"); cookie.setMaxAge(0); response.addCookie(cookie);
        return ApiEnvelope.ok(Map.of("loggedOut", true));
    }

    @GetMapping("/api/auth/me")
    public Map<String,Object> me(@CookieValue(value="SESSION", required=false) String sessionId) { return ApiEnvelope.ok(service.me(sessionId)); }
    @GetMapping("/api/admin/navigation")
    public Map<String,Object> navigation(@CookieValue(value="SESSION", required=false) String sessionId) { return ApiEnvelope.ok(service.navigation(sessionId)); }

    @GetMapping("/api/admin/users")
    public Map<String,Object> users(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("users", q)); }
    @PatchMapping("/api/admin/users/{userId}")
    public Map<String,Object> updateUser(@CookieValue(value="SESSION", required=false) String s, @PathVariable String userId, @RequestBody Map<String,Object> b) { Map<String,Object> me=service.authenticate(s); service.updateUserAccess(userId,b,String.valueOf(me.get("userId"))); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/organizations")
    public Map<String,Object> orgs(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("organizations", q)); }
    @GetMapping("/api/admin/organizations/tree")
    public Map<String,Object> orgTree(@CookieValue(value="SESSION", required=false) String s) { service.authenticate(s); return ApiEnvelope.ok(service.list("organizationTree", Map.of())); }
    @PutMapping("/api/admin/organizations/{orgCode}/relationship")
    public Map<String,Object> orgRel(@CookieValue(value="SESSION", required=false) String s, @PathVariable String orgCode, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateOrganizationRelationship(orgCode,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/roles")
    public Map<String,Object> roles(@CookieValue(value="SESSION", required=false) String s) { service.authenticate(s); return ApiEnvelope.ok(service.list("roles", Map.of())); }
    @PutMapping("/api/admin/roles/{roleCode}")
    public Map<String,Object> role(@CookieValue(value="SESSION", required=false) String s, @PathVariable String roleCode, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateRole(roleCode,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/user-roles")
    public Map<String,Object> userRoles(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("userRoles", q)); }
    @PutMapping("/api/admin/users/{userId}/roles")
    public Map<String,Object> replaceRoles(@CookieValue(value="SESSION", required=false) String s, @PathVariable String userId, @RequestBody Map<String,Object> b) { service.authenticate(s); service.replaceUserRoles(userId,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/menu-permissions")
    public Map<String,Object> permissions(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("menuPermissions", q)); }
    @PutMapping("/api/admin/menu-permissions")
    public Map<String,Object> savePermissions(@CookieValue(value="SESSION", required=false) String s, @RequestBody Map<String,Object> b) { service.authenticate(s); service.saveMenuPermissions(b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/menus/tree")
    public Map<String,Object> menuTree(@CookieValue(value="SESSION", required=false) String s) { service.authenticate(s); return ApiEnvelope.ok(service.list("menuTree", Map.of())); }
    @PutMapping("/api/admin/menus/{menuId}/structure")
    public Map<String,Object> menuStructure(@CookieValue(value="SESSION", required=false) String s, @PathVariable String menuId, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateMenuStructure(menuId,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @PatchMapping("/api/admin/menus/order")
    public Map<String,Object> order(@CookieValue(value="SESSION", required=false) String s, @RequestBody Map<String,Object> b) { service.authenticate(s); service.reorderMenus(b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/menus")
    public Map<String,Object> menus(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("menus", q)); }
    @PostMapping("/api/admin/menus")
    public Map<String,Object> createMenu(@CookieValue(value="SESSION", required=false) String s, @RequestBody Map<String,Object> b) { service.authenticate(s); service.createMenu(b); return ApiEnvelope.ok(Map.of("created", true)); }
    @PutMapping("/api/admin/menus/{menuId}")
    public Map<String,Object> updateMenu(@CookieValue(value="SESSION", required=false) String s, @PathVariable String menuId, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateMenu(menuId,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/code-groups")
    public Map<String,Object> codeGroups(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("codeGroups", q)); }
    @PostMapping("/api/admin/code-groups")
    public Map<String,Object> createGroup(@CookieValue(value="SESSION", required=false) String s, @RequestBody Map<String,Object> b) { service.authenticate(s); service.createCodeGroup(b); return ApiEnvelope.ok(Map.of("created", true)); }
    @PutMapping("/api/admin/code-groups/{groupId}")
    public Map<String,Object> updateGroup(@CookieValue(value="SESSION", required=false) String s, @PathVariable String groupId, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateCodeGroup(groupId,b); return ApiEnvelope.ok(Map.of("updated", true)); }
    @GetMapping("/api/admin/code-details")
    public Map<String,Object> codeDetails(@CookieValue(value="SESSION", required=false) String s, @RequestParam Map<String,String> q) { service.authenticate(s); return ApiEnvelope.ok(service.list("codeDetails", q)); }
    @PostMapping("/api/admin/code-details")
    public Map<String,Object> createDetail(@CookieValue(value="SESSION", required=false) String s, @RequestBody Map<String,Object> b) { service.authenticate(s); service.createCodeDetail(b); return ApiEnvelope.ok(Map.of("created", true)); }
    @PutMapping("/api/admin/code-details/{detailId}")
    public Map<String,Object> updateDetail(@CookieValue(value="SESSION", required=false) String s, @PathVariable String detailId, @RequestBody Map<String,Object> b) { service.authenticate(s); service.updateCodeDetail(detailId,b); return ApiEnvelope.ok(Map.of("updated", true)); }
}

package kr.ac.knue.performance;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface CommonMapper {
    @SelectProvider(type = SqlProvider.class, method = "users")
    List<Map<String, Object>> users(@Param("filter") String filter, @Param("roleCode") String roleCode, @Param("systemEnabled") Boolean systemEnabled, @Param("limit") int limit, @Param("offset") int offset);

    @SelectProvider(type = SqlProvider.class, method = "organizations")
    List<Map<String, Object>> organizations(@Param("filter") String filter, @Param("limit") int limit, @Param("offset") int offset);

    @SelectProvider(type = SqlProvider.class, method = "roles")
    List<Map<String, Object>> roles(@Param("filter") String filter);

    @SelectProvider(type = SqlProvider.class, method = "userRoles")
    List<Map<String, Object>> userRoles(@Param("filter") String filter, @Param("limit") int limit, @Param("offset") int offset);

    @SelectProvider(type = SqlProvider.class, method = "menuPermissions")
    List<Map<String, Object>> menuPermissions(@Param("targetType") String targetType, @Param("targetId") String targetId);

    @SelectProvider(type = SqlProvider.class, method = "menus")
    List<Map<String, Object>> menus(@Param("filter") String filter);

    @SelectProvider(type = SqlProvider.class, method = "codeGroups")
    List<Map<String, Object>> codeGroups(@Param("filter") String filter);

    @SelectProvider(type = SqlProvider.class, method = "detailCodes")
    List<Map<String, Object>> detailCodes(@Param("groupId") String groupId, @Param("filter") String filter);

    @Select("SELECT ua.user_id AS \"userId\", ua.login_id AS \"loginId\", ks.name AS \"name\" FROM user_account ua LEFT JOIN korus_staff_snapshot ks ON ks.staff_no = ua.korus_staff_id WHERE ua.login_id = #{loginId} AND ua.password_hash = #{password} AND ua.system_enabled = TRUE AND ua.status = 'ACTIVE'")
    Map<String, Object> authenticate(@Param("loginId") String loginId, @Param("password") String password);

    @Select("SELECT ua.user_id AS \"userId\", ua.login_id AS \"loginId\", ks.name AS \"name\" FROM session s JOIN user_account ua ON ua.user_id=s.user_id LEFT JOIN korus_staff_snapshot ks ON ks.staff_no = ua.korus_staff_id WHERE s.session_id=#{sessionId} AND s.status='ACTIVE' AND s.expires_at > CURRENT_TIMESTAMP")
    Map<String, Object> sessionUser(@Param("sessionId") String sessionId);

    @Select("SELECT role_code FROM user_role_assignment WHERE user_id=#{userId} AND status='ACTIVE' ORDER BY role_code")
    List<String> rolesForUser(@Param("userId") String userId);

    @Insert("INSERT INTO session (session_id, user_id, expires_at, status) VALUES (#{sessionId}, #{userId}, CURRENT_TIMESTAMP + INTERVAL '8' HOUR, 'ACTIVE')")
    void createSession(@Param("sessionId") String sessionId, @Param("userId") String userId);

    @Update("UPDATE session SET status='EXPIRED' WHERE session_id=#{sessionId}")
    int expireSession(@Param("sessionId") String sessionId);

    @SelectProvider(type = SqlProvider.class, method = "byId")
    Map<String, Object> byId(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") String id);

    @Update("UPDATE user_account SET system_enabled=#{enabled}, status=CASE WHEN #{enabled}=TRUE THEN 'ACTIVE' ELSE 'DISABLED' END, updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    int updateUserUsage(@Param("userId") String userId, @Param("enabled") boolean enabled);

    @Update("UPDATE organization SET parent_organization_code=#{parent}, effective_start_date=CAST(#{start} AS DATE), effective_end_date=CAST(#{end} AS DATE), updated_at=CURRENT_TIMESTAMP WHERE organization_code=#{code}")
    int updateOrganizationRelation(@Param("code") String code, @Param("parent") String parent, @Param("start") String start, @Param("end") String end);

    @InsertProvider(type = SqlProvider.class, method = "insertUserRole")
    int insertUserRole(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateUserRole")
    int updateUserRole(Map<String, Object> row);

    @Update("UPDATE user_role_assignment SET status='REVOKED', updated_at=CURRENT_TIMESTAMP WHERE assignment_id=#{id} AND status='ACTIVE'")
    int revokeUserRole(@Param("id") String id);

    @InsertProvider(type = SqlProvider.class, method = "insertRole")
    int insertRole(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateRole")
    int updateRole(Map<String, Object> row);

    @InsertProvider(type = SqlProvider.class, method = "insertMenuPermission")
    int insertMenuPermission(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateMenuPermission")
    int updateMenuPermission(Map<String, Object> row);

    @Update("UPDATE menu SET parent_menu_id=#{parent}, display_order=#{order}, updated_at=CURRENT_TIMESTAMP WHERE menu_id=#{id}")
    int updateMenuParent(@Param("id") String id, @Param("parent") String parent, @Param("order") int order);

    @Update("UPDATE menu SET display_order=#{order}, updated_at=CURRENT_TIMESTAMP WHERE menu_id=#{id}")
    int updateMenuOrder(@Param("id") String id, @Param("order") int order);

    @InsertProvider(type = SqlProvider.class, method = "insertMenu")
    int insertMenu(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateMenu")
    int updateMenu(Map<String, Object> row);

    @InsertProvider(type = SqlProvider.class, method = "insertCodeGroup")
    int insertCodeGroup(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateCodeGroup")
    int updateCodeGroup(Map<String, Object> row);

    @InsertProvider(type = SqlProvider.class, method = "insertDetailCode")
    int insertDetailCode(Map<String, Object> row);

    @UpdateProvider(type = SqlProvider.class, method = "updateDetailCode")
    int updateDetailCode(Map<String, Object> row);

    @Insert("INSERT INTO change_history (history_id, entity_name, entity_id, before_value, after_value, reason, actor_user_id) VALUES (#{id}, #{entity}, #{entityId}, #{beforeValue}, #{afterValue}, #{reason}, #{actor})")
    void history(@Param("id") String id, @Param("entity") String entity, @Param("entityId") String entityId, @Param("beforeValue") String beforeValue, @Param("afterValue") String afterValue, @Param("reason") String reason, @Param("actor") String actor);
}

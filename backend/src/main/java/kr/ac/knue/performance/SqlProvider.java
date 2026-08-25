package kr.ac.knue.performance;

import java.util.Map;

public class SqlProvider {
    public static String users(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT ua.user_id AS \"userId\", ua.login_id AS \"loginId\", ua.system_enabled AS \"systemEnabled\", ua.status AS \"status\", ks.staff_no AS \"staffNo\", ks.name AS \"name\", ks.organization_code AS \"organizationCode\", org.organization_name AS \"organizationName\", ks.position AS \"position\", ks.rank AS \"rank\", ks.employment_status AS \"employmentStatus\", ks.retirement_date AS \"retirementDate\", ks.last_synced_at AS \"lastSyncedAt\", COALESCE(string_agg(ura.role_code, ',' ORDER BY ura.role_code), '') AS \"roleCodes\" FROM user_account ua LEFT JOIN korus_staff_snapshot ks ON ks.staff_no=ua.korus_staff_id LEFT JOIN organization org ON org.organization_code=ks.organization_code LEFT JOIN user_role_assignment ura ON ura.user_id=ua.user_id AND ura.status='ACTIVE' WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (ua.user_id LIKE CONCAT('%', #{filter}, '%') OR ua.login_id LIKE CONCAT('%', #{filter}, '%') OR ks.staff_no LIKE CONCAT('%', #{filter}, '%') OR ks.name LIKE CONCAT('%', #{filter}, '%') OR org.organization_name LIKE CONCAT('%', #{filter}, '%') OR ks.rank LIKE CONCAT('%', #{filter}, '%') OR ks.employment_status LIKE CONCAT('%', #{filter}, '%'))");
        if (has(p, "roleCode")) sql.append(" AND ura.role_code = #{roleCode}");
        if (p.get("systemEnabled") != null) sql.append(" AND ua.system_enabled = #{systemEnabled}");
        sql.append(" GROUP BY ua.user_id, ua.login_id, ua.system_enabled, ua.status, ks.staff_no, ks.name, ks.organization_code, org.organization_name, ks.position, ks.rank, ks.employment_status, ks.retirement_date, ks.last_synced_at ORDER BY ks.last_synced_at DESC LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public static String organizations(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT organization_code AS \"organizationCode\", organization_name AS \"organizationName\", organization_type AS \"organizationType\", parent_organization_code AS \"parentOrganizationCode\", effective_start_date AS \"effectiveStartDate\", effective_end_date AS \"effectiveEndDate\", use_yn AS \"useYn\" FROM organization WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (organization_code LIKE CONCAT('%', #{filter}, '%') OR organization_name LIKE CONCAT('%', #{filter}, '%') OR organization_type LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY organization_code LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public static String roles(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT role_code AS \"roleCode\", role_name AS \"roleName\", purpose AS \"purpose\", assignment_criteria AS \"assignmentCriteria\", default_data_scope AS \"defaultDataScope\", use_yn AS \"useYn\" FROM role WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (role_code LIKE CONCAT('%', #{filter}, '%') OR role_name LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY role_code");
        return sql.toString();
    }

    public static String userRoles(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT ura.assignment_id AS \"assignmentId\", ura.user_id AS \"userId\", ks.name AS \"userName\", ura.role_code AS \"roleCode\", r.role_name AS \"roleName\", ura.assignment_type AS \"assignmentType\", ura.valid_from AS \"validFrom\", ura.valid_to AS \"validTo\", ura.approver_user_id AS \"approverUserId\", ura.status AS \"status\" FROM user_role_assignment ura JOIN user_account ua ON ua.user_id=ura.user_id LEFT JOIN korus_staff_snapshot ks ON ks.staff_no=ua.korus_staff_id JOIN role r ON r.role_code=ura.role_code WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (ura.assignment_id LIKE CONCAT('%', #{filter}, '%') OR ura.user_id LIKE CONCAT('%', #{filter}, '%') OR ks.name LIKE CONCAT('%', #{filter}, '%') OR ura.role_code LIKE CONCAT('%', #{filter}, '%') OR ura.assignment_type LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY ura.valid_from DESC LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public static String menuPermissions(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT mp.permission_id AS \"permissionId\", mp.target_type AS \"targetType\", mp.target_id AS \"targetId\", mp.menu_id AS \"menuId\", m.menu_name AS \"menuName\", m.parent_menu_id AS \"parentMenuId\", m.menu_level AS \"menuLevel\", mp.allowed AS \"allowed\", mp.use_yn AS \"useYn\" FROM menu_permission mp JOIN menu m ON m.menu_id=mp.menu_id WHERE 1=1");
        if (has(p, "targetType")) sql.append(" AND mp.target_type = #{targetType}");
        if (has(p, "targetId")) sql.append(" AND mp.target_id = #{targetId}");
        sql.append(" ORDER BY m.display_order, m.menu_id");
        return sql.toString();
    }

    public static String menus(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT menu_id AS \"menuId\", parent_menu_id AS \"parentMenuId\", menu_level AS \"menuLevel\", menu_name AS \"menuName\", display_order AS \"displayOrder\", screen_id AS \"screenId\", url AS \"url\", icon AS \"icon\", business_category AS \"businessCategory\", description AS \"description\", use_yn AS \"useYn\" FROM menu WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (menu_id LIKE CONCAT('%', #{filter}, '%') OR menu_name LIKE CONCAT('%', #{filter}, '%') OR screen_id LIKE CONCAT('%', #{filter}, '%') OR url LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY display_order, menu_id");
        return sql.toString();
    }

    public static String codeGroups(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT group_id AS \"groupId\", group_name AS \"groupName\", description AS \"description\", management_department AS \"managementDepartment\", use_yn AS \"useYn\" FROM code_group WHERE 1=1");
        if (has(p, "filter")) sql.append(" AND (group_id LIKE CONCAT('%', #{filter}, '%') OR group_name LIKE CONCAT('%', #{filter}, '%') OR management_department LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY group_id");
        return sql.toString();
    }

    public static String detailCodes(Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("SELECT group_id AS \"groupId\", code_value AS \"codeValue\", code_name AS \"codeName\", parent_code_value AS \"parentCodeValue\", sort_order AS \"sortOrder\", extra_attributes AS \"extraAttributes\", valid_from AS \"validFrom\", valid_to AS \"validTo\", use_yn AS \"useYn\" FROM detail_code WHERE group_id = #{groupId}");
        if (has(p, "filter")) sql.append(" AND (code_value LIKE CONCAT('%', #{filter}, '%') OR code_name LIKE CONCAT('%', #{filter}, '%'))");
        sql.append(" ORDER BY sort_order, code_value");
        return sql.toString();
    }

    public static String byId(Map<String, Object> p) {
        String table = safe((String) p.get("table"));
        String idColumn = safe((String) p.get("idColumn"));
        return "SELECT * FROM " + table + " WHERE " + idColumn + " = #{id}";
    }

    public static String insertUserRole(Map<String, Object> r) {
        return "INSERT INTO user_role_assignment (assignment_id,user_id,role_code,assignment_type,valid_from,valid_to,approver_user_id,status) VALUES (#{assignmentId},#{userId},#{roleCode},#{assignmentType},CAST(#{validFrom} AS DATE),CAST(#{validTo} AS DATE),#{approverUserId},'ACTIVE')";
    }

    public static String updateUserRole(Map<String, Object> r) {
        return "UPDATE user_role_assignment SET user_id=#{userId}, role_code=#{roleCode}, assignment_type=#{assignmentType}, valid_from=CAST(#{validFrom} AS DATE), valid_to=CAST(#{validTo} AS DATE), approver_user_id=#{approverUserId}, updated_at=CURRENT_TIMESTAMP WHERE assignment_id=#{assignmentId}";
    }

    public static String insertRole(Map<String, Object> r) {
        return "INSERT INTO role (role_code,role_name,purpose,assignment_criteria,default_data_scope,use_yn) VALUES (#{roleCode},#{roleName},#{purpose},#{assignmentCriteria},#{defaultDataScope},#{useYn})";
    }

    public static String updateRole(Map<String, Object> r) {
        return "UPDATE role SET role_name=#{roleName}, purpose=#{purpose}, assignment_criteria=#{assignmentCriteria}, default_data_scope=#{defaultDataScope}, use_yn=#{useYn}, updated_at=CURRENT_TIMESTAMP WHERE role_code=#{roleCode}";
    }

    public static String insertMenuPermission(Map<String, Object> r) {
        return "INSERT INTO menu_permission (permission_id,target_type,target_id,menu_id,allowed,use_yn) VALUES (#{permissionId},#{targetType},#{targetId},#{menuId},#{allowed},'Y')";
    }

    public static String updateMenuPermission(Map<String, Object> r) {
        return "UPDATE menu_permission SET allowed=#{allowed}, use_yn='Y', updated_at=CURRENT_TIMESTAMP WHERE target_type=#{targetType} AND target_id=#{targetId} AND menu_id=#{menuId}";
    }

    public static String insertMenu(Map<String, Object> r) {
        return "INSERT INTO menu (menu_id,parent_menu_id,menu_level,menu_name,display_order,screen_id,url,icon,business_category,description,use_yn) VALUES (#{menuId},#{parentMenuId},#{menuLevel},#{menuName},#{displayOrder},#{screenId},#{url},#{icon},#{businessCategory},#{description},#{useYn})";
    }

    public static String updateMenu(Map<String, Object> r) {
        return "UPDATE menu SET parent_menu_id=#{parentMenuId}, menu_level=#{menuLevel}, menu_name=#{menuName}, display_order=#{displayOrder}, screen_id=#{screenId}, url=#{url}, icon=#{icon}, business_category=#{businessCategory}, description=#{description}, use_yn=#{useYn}, updated_at=CURRENT_TIMESTAMP WHERE menu_id=#{menuId}";
    }

    public static String insertCodeGroup(Map<String, Object> r) {
        return "INSERT INTO code_group (group_id,group_name,description,management_department,use_yn) VALUES (#{groupId},#{groupName},#{description},#{managementDepartment},#{useYn})";
    }

    public static String updateCodeGroup(Map<String, Object> r) {
        return "UPDATE code_group SET group_name=#{groupName}, description=#{description}, management_department=#{managementDepartment}, use_yn=#{useYn}, updated_at=CURRENT_TIMESTAMP WHERE group_id=#{groupId}";
    }

    public static String insertDetailCode(Map<String, Object> r) {
        return "INSERT INTO detail_code (group_id,code_value,code_name,parent_code_value,sort_order,extra_attributes,valid_from,valid_to,use_yn) VALUES (#{groupId},#{codeValue},#{codeName},#{parentCodeValue},#{sortOrder},#{extraAttributes},CAST(#{validFrom} AS DATE),CAST(#{validTo} AS DATE),#{useYn})";
    }

    public static String updateDetailCode(Map<String, Object> r) {
        return "UPDATE detail_code SET code_name=#{codeName}, parent_code_value=#{parentCodeValue}, sort_order=#{sortOrder}, extra_attributes=#{extraAttributes}, valid_from=CAST(#{validFrom} AS DATE), valid_to=CAST(#{validTo} AS DATE), use_yn=#{useYn}, updated_at=CURRENT_TIMESTAMP WHERE group_id=#{groupId} AND code_value=#{codeValue}";
    }

    private static boolean has(Map<String, Object> p, String key) {
        Object value = p.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private static String safe(String input) {
        if (input == null || !input.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("invalid identifier");
        return input;
    }
}

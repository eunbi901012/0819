
package kr.ac.knue.performance.common;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommonMapper {
    @Select("""
        select u.user_id as "userId", u.login_id as "loginId", u.password_hash as "passwordHash", u.system_enabled as "systemEnabled",
               string_agg(ra.role_code, ',' order by ra.role_code) filter (where ra.status='ACTIVE') as "roleCodes"
        from user_account u left join user_role_assignment ra on ra.user_id=u.user_id group by u.user_id
        """)
    List<Map<String, Object>> usersForAuth();

    @Select("""
        select s.session_id as "sessionId", s.user_id as "userId", u.login_id as "loginId", s.status,
               string_agg(ra.role_code, ',' order by ra.role_code) filter (where ra.status='ACTIVE') as "roleCodes"
        from session s join user_account u on u.user_id=s.user_id
        left join user_role_assignment ra on ra.user_id=u.user_id
        where s.session_id=#{sessionId} and s.expires_at > now()
        group by s.session_id, u.user_id
        """)
    Map<String, Object> session(@Param("sessionId") String sessionId);

    @Insert("insert into session(session_id,user_id,created_at,expires_at,status) values(#{sessionId},#{userId},now(),now()+ interval '8' hour,'ACTIVE')")
    void createSession(@Param("sessionId") String sessionId, @Param("userId") String userId);

    @Update("update session set status='EXPIRED' where session_id=#{sessionId}")
    void expireSession(@Param("sessionId") String sessionId);

    @Select("""
        select m.menu_id as "menuId", m.parent_menu_id as "parentMenuId", m.menu_name as "menuName", m.menu_level as "menuLevel",
               m.sort_order as "sortOrder", coalesce(m.screen_id, '') as "screenId", coalesce(m.url, '') as url, m.icon, m.business_category as "businessCategory",
               m.description, case when m.use_yn then 'Y' else 'N' end as "useYn"
        from menu m where m.use_yn=true order by m.menu_level, m.sort_order, m.menu_id
        """)
    List<Map<String, Object>> menus();

    @Select("""
        select distinct m.menu_id as "menuId", m.parent_menu_id as "parentMenuId", m.menu_name as "menuName", m.menu_level as "menuLevel",
               m.sort_order as "sortOrder", coalesce(m.screen_id, '') as "screenId", coalesce(m.url, '') as url, m.icon, m.business_category as "businessCategory", m.description
        from menu m join menu_permission p on p.menu_id=m.menu_id and p.allow_access=true and p.use_yn=true
        where p.target_type='ROLE' and p.target_id='R09' and m.use_yn=true
        order by m.menu_level, m.sort_order, m.menu_id
        """)
    List<Map<String, Object>> navigation(@Param("roleCodes") String[] roleCodes);

    @Select("""
        <script>
        select u.user_id as "userId", k.employee_no as "employeeNo", k.name, k.organization_code as "organizationCode", o.org_name as "organizationName",
               k.rank, k.employment_status as "employmentStatus", k.position, k.retirement_date as "retirementDate", k.last_synced_at as "lastSyncedAt",
               case when u.system_enabled then 'Y' else 'N' end as "systemEnabled", coalesce(string_agg(ra.role_code, ',' order by ra.role_code),'') as "roleCodes"
        from user_account u join korus_staff_snapshot k on k.employee_no=u.employee_no
        left join organization o on o.org_code=k.organization_code
        left join user_role_assignment ra on ra.user_id=u.user_id and ra.status='ACTIVE'
        where 1=1
        <if test='filter != null and filter != ""'>and (k.employee_no ilike concat('%',#{filter},'%') or k.name ilike concat('%',#{filter},'%') or o.org_name ilike concat('%',#{filter},'%'))</if>
        group by u.user_id,k.employee_no,o.org_name order by k.employee_no
        </script>
        """)
    List<Map<String, Object>> listUsers(@Param("filter") String filter);

    @Update("update user_account set system_enabled=#{enabled}, status=#{status}, updated_at=now() where user_id=#{userId}")
    int updateUser(@Param("userId") String userId, @Param("enabled") boolean enabled, @Param("status") String status);

    @Delete("delete from user_role_assignment where user_id=#{userId} and assignment_type='MANUAL'")
    void deleteManualRoles(@Param("userId") String userId);

    @Insert("insert into user_role_assignment(assignment_id,user_id,role_code,assignment_type,effective_start_date,effective_end_date,approver_user_id,status) values(#{id},#{userId},#{roleCode},'MANUAL',current_date,null,#{approver},'ACTIVE')")
    void insertManualRole(@Param("id") String id, @Param("userId") String userId, @Param("roleCode") String roleCode, @Param("approver") String approver);

    @Select("""
        <script>
        select org_code as "orgCode", org_name as "orgName", org_type as "orgType", case when use_yn then 'Y' else 'N' end as "useYn"
        from organization where 1=1
        <if test='filter != null and filter != ""'>and (org_code ilike concat('%',#{filter},'%') or org_name ilike concat('%',#{filter},'%'))</if>
        order by org_code
        </script>
        """)
    List<Map<String, Object>> listOrganizations(@Param("filter") String filter);

    @Select("""
        select o.org_code as "orgCode", o.org_name as "orgName", o.org_type as "orgType", r.parent_org_code as "parentOrgCode",
               r.effective_start_date as "effectiveStartDate", r.effective_end_date as "effectiveEndDate", r.status
        from organization o left join organization_relation r on r.org_code=o.org_code and r.status='ACTIVE'
        order by coalesce(r.parent_org_code,''), o.org_code
        """)
    List<Map<String, Object>> organizationTreeRows();

    @Update("update organization_relation set parent_org_code=#{parentOrgCode}, effective_start_date=#{startDate}::date, effective_end_date=#{endDate}::date, status=#{status}, updated_at=now() where org_code=#{orgCode}")
    int updateOrganizationRelation(@Param("orgCode") String orgCode, @Param("parentOrgCode") String parentOrgCode, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("status") String status);

    @Select("""
        select role_code as "roleCode", role_name as "roleName", purpose, grant_criteria as "grantCriteria", data_scope_default as "dataScopeDefault",
               case when use_yn then 'Y' else 'N' end as "useYn" from role order by role_code
        """)
    List<Map<String, Object>> listRoles();

    @Update("update role set role_name=#{roleName}, purpose=#{purpose}, grant_criteria=#{grantCriteria}, data_scope_default=#{dataScopeDefault}, use_yn=#{useYn}, updated_at=now() where role_code=#{roleCode}")
    int updateRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName, @Param("purpose") String purpose, @Param("grantCriteria") String grantCriteria, @Param("dataScopeDefault") String dataScopeDefault, @Param("useYn") boolean useYn);

    @Select("""
        <script>
        select a.assignment_id as "assignmentId", u.user_id as "userId", k.employee_no as "employeeNo", k.name, a.role_code as "roleCode", r.role_name as "roleName",
               a.assignment_type as "assignmentType", a.effective_start_date as "effectiveStartDate", a.effective_end_date as "effectiveEndDate",
               a.approver_user_id as "approverUserId", a.status
        from user_role_assignment a join user_account u on u.user_id=a.user_id join korus_staff_snapshot k on k.employee_no=u.employee_no join role r on r.role_code=a.role_code
        where 1=1
        <if test='filter != null and filter != ""'>and (k.employee_no ilike concat('%',#{filter},'%') or k.name ilike concat('%',#{filter},'%') or a.role_code=#{filter})</if>
        order by k.employee_no,a.role_code
        </script>
        """)
    List<Map<String, Object>> listUserRoles(@Param("filter") String filter);

    @Delete("delete from user_role_assignment where user_id=#{userId}")
    void deleteRoles(@Param("userId") String userId);

    @Insert("insert into user_role_assignment(assignment_id,user_id,role_code,assignment_type,effective_start_date,effective_end_date,approver_user_id,status) values(#{id},#{userId},#{roleCode},#{assignmentType},#{startDate}::date,#{endDate}::date,#{approver},#{status})")
    void insertRoleAssignment(@Param("id") String id, @Param("userId") String userId, @Param("roleCode") String roleCode, @Param("assignmentType") String assignmentType, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("approver") String approver, @Param("status") String status);

    @Select("""
        <script>
        select p.permission_id as "permissionId", p.target_type as "targetType", p.target_id as "targetId", p.menu_id as "menuId",
               mt.menu_name as "topMenuName", mm.menu_name as "middleMenuName", m.menu_name as "menuName", case when p.allow_access then 'Y' else 'N' end as "allowAccess"
        from menu_permission p join menu m on m.menu_id=p.menu_id left join menu mm on mm.menu_id=m.parent_menu_id left join menu mt on mt.menu_id=mm.parent_menu_id
        where 1=1
        <if test='targetType != null and targetType != ""'>and p.target_type=#{targetType}</if>
        <if test='targetId != null and targetId != ""'>and p.target_id=#{targetId}</if>
        order by mt.sort_order, mm.sort_order, m.sort_order
        </script>
        """)
    List<Map<String, Object>> listMenuPermissions(@Param("targetType") String targetType, @Param("targetId") String targetId);

    @Update("update menu_permission set allow_access=#{allow}, use_yn=true where target_type=#{targetType} and target_id=#{targetId} and menu_id=#{menuId}")
    int updatePermission(@Param("targetType") String targetType, @Param("targetId") String targetId, @Param("menuId") String menuId, @Param("allow") boolean allow);

    @Insert("insert into menu_permission(permission_id,target_type,target_id,menu_id,allow_access,use_yn) values(#{id},#{targetType},#{targetId},#{menuId},#{allow},true)")
    void insertPermission(@Param("id") String id, @Param("targetType") String targetType, @Param("targetId") String targetId, @Param("menuId") String menuId, @Param("allow") boolean allow);

    @Update("update menu set parent_menu_id=#{parentMenuId}, menu_level=#{menuLevel}, updated_at=now() where menu_id=#{menuId}")
    int updateMenuStructure(@Param("menuId") String menuId, @Param("parentMenuId") String parentMenuId, @Param("menuLevel") int menuLevel);

    @Update("update menu set sort_order=#{sortOrder}, updated_at=now() where menu_id=#{menuId}")
    int updateMenuOrder(@Param("menuId") String menuId, @Param("sortOrder") int sortOrder);

    @Insert("insert into menu(menu_id,parent_menu_id,menu_name,menu_level,sort_order,screen_id,url,icon,business_category,description,use_yn,created_at,updated_at) values(#{menuId},#{parentMenuId},#{menuName},#{menuLevel},#{sortOrder},#{screenId},#{url},#{icon},#{businessCategory},#{description},#{useYn},now(),now())")
    void insertMenu(Map<String, Object> m);

    @Update("update menu set menu_name=#{menuName}, screen_id=#{screenId}, url=#{url}, icon=#{icon}, business_category=#{businessCategory}, description=#{description}, use_yn=#{useYn}, updated_at=now() where menu_id=#{menuId}")
    int updateMenu(Map<String, Object> m);

    @Select("""
        <script>
        select group_id as "groupId", group_name as "groupName", description, managing_department as "managingDepartment", case when use_yn then 'Y' else 'N' end as "useYn"
        from code_group where 1=1
        <if test='filter != null and filter != ""'>and (group_id ilike concat('%',#{filter},'%') or group_name ilike concat('%',#{filter},'%') or managing_department ilike concat('%',#{filter},'%'))</if>
        order by group_id
        </script>
        """)
    List<Map<String, Object>> listCodeGroups(@Param("filter") String filter);

    @Insert("insert into code_group(group_id,group_name,description,managing_department,use_yn,created_at,updated_at) values(#{groupId},#{groupName},#{description},#{managingDepartment},#{useYn},now(),now())")
    void insertCodeGroup(Map<String, Object> m);

    @Update("update code_group set group_name=#{groupName}, description=#{description}, managing_department=#{managingDepartment}, use_yn=#{useYn}, updated_at=now() where group_id=#{groupId}")
    int updateCodeGroup(Map<String, Object> m);

    @Select("""
        <script>
        select detail_id as "detailId", group_id as "groupId", code_value as "codeValue", code_name as "codeName", parent_code_value as "parentCodeValue",
               sort_order as "sortOrder", extra_attributes::text as "extraAttributes", case when use_yn then 'Y' else 'N' end as "useYn"
        from code_detail where 1=1
        <if test='groupId != null and groupId != ""'>and group_id=#{groupId}</if>
        <if test='filter != null and filter != ""'>and (code_value ilike concat('%',#{filter},'%') or code_name ilike concat('%',#{filter},'%'))</if>
        order by group_id,sort_order,code_value
        </script>
        """)
    List<Map<String, Object>> listCodeDetails(@Param("groupId") String groupId, @Param("filter") String filter);

    @Insert("insert into code_detail(detail_id,group_id,code_value,code_name,parent_code_value,sort_order,extra_attributes,use_yn,created_at,updated_at) values(#{detailId},#{groupId},#{codeValue},#{codeName},#{parentCodeValue},#{sortOrder},#{extraAttributes}::jsonb,#{useYn},now(),now())")
    void insertCodeDetail(Map<String, Object> m);

    @Update("update code_detail set code_value=#{codeValue}, code_name=#{codeName}, parent_code_value=#{parentCodeValue}, sort_order=#{sortOrder}, extra_attributes=#{extraAttributes}::jsonb, use_yn=#{useYn}, updated_at=now() where detail_id=#{detailId}")
    int updateCodeDetail(Map<String, Object> m);
}
